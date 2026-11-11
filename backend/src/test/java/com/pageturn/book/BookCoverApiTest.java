package com.pageturn.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.pageturn.ApiTestSupport;

class BookCoverApiTest extends ApiTestSupport {

    private static byte[] picture(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(109, 40, 217));
        g.fillRect(0, 0, width, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static MockMultipartHttpServletRequestBuilder upload(Long bookId, byte[] bytes, String name, String type) {
        MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PUT, "/api/v1/admin/books/" + bookId + "/cover");
        request.file(new MockMultipartFile("file", name, type, bytes));
        return request;
    }

    @Test
    void uploadedCoverIsShrunkReEncodedAndServedToMembers() throws Exception {
        TestUser librarian = librarian("cover-lib");
        TestUser member = member("cover-reader");
        Long bookId = createBook(librarian, "Covered Book", 1);

        mockMvc.perform(as(librarian, upload(bookId, picture("png", 1200, 1800), "big.png", "image/png")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverVersion").isNumber());

        byte[] served = mockMvc.perform(as(member, get("/api/v1/books/" + bookId + "/cover")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(result.getWidth()).isEqualTo(600);
        assertThat(result.getHeight()).isEqualTo(900);
        // Not signed in: no picture.
        mockMvc.perform(get("/api/v1/books/" + bookId + "/cover")).andExpect(status().isUnauthorized());
    }

    @Test
    void hiddenDataInsideAPictureIsRemoved() throws Exception {
        TestUser librarian = librarian("cover-meta");
        Long bookId = createBook(librarian, "Metadata Book", 1);
        byte[] jpeg = picture("jpg", 400, 600);
        // Insert a JPEG comment segment (like camera/GPS metadata) right after the start marker.
        byte[] secret = "GPS 51.5007,-0.1246 <script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream tampered = new ByteArrayOutputStream();
        tampered.write(jpeg, 0, 2);
        tampered.write(new byte[] {(byte) 0xFF, (byte) 0xFE, 0, (byte) (secret.length + 2)});
        tampered.write(secret);
        tampered.write(jpeg, 2, jpeg.length - 2);

        mockMvc.perform(as(librarian, upload(bookId, tampered.toByteArray(), "photo.jpg", "image/jpeg"))).andExpect(status().isOk());
        byte[] served = mockMvc.perform(as(librarian, get("/api/v1/books/" + bookId + "/cover")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("GPS").doesNotContain("script");
    }

    @Test
    void filesThatAreNotRealPicturesAreRejected() throws Exception {
        TestUser librarian = librarian("cover-fake");
        Long bookId = createBook(librarian, "Fake Cover Book", 1);
        byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(as(librarian, upload(bookId, html, "cover.png", "image/png")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This file isn't a valid JPG or PNG picture."));
        // Starts like a PNG but is broken inside.
        byte[] broken = new byte[2000];
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, broken, 0, 8);
        mockMvc.perform(as(librarian, upload(bookId, broken, "cover.png", "image/png"))).andExpect(status().isBadRequest());
        // GIF is a real picture but not an accepted format.
        mockMvc.perform(as(librarian, upload(bookId, picture("gif", 200, 300), "cover.gif", "image/gif")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(librarian, upload(bookId, picture("png", 20, 20), "tiny.png", "image/png")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(librarian, get("/api/v1/books/" + bookId))).andExpect(jsonPath("$.coverVersion").doesNotExist());
    }

    @Test
    void onlyLibrariansCanChangeCoversAndBigFilesAreRefused() throws Exception {
        TestUser librarian = librarian("cover-guard");
        TestUser member = member("cover-member");
        Long bookId = createBook(librarian, "Guarded Cover", 1);
        byte[] png = picture("png", 300, 450);

        mockMvc.perform(as(member, upload(bookId, png, "c.png", "image/png"))).andExpect(status().isForbidden());
        mockMvc.perform(upload(bookId, png, "c.png", "image/png").cookie(librarian.session()))
                .andExpect(status().isForbidden()); // no CSRF token
        mockMvc.perform(as(librarian, upload(bookId, new byte[3_500_000], "huge.png", "image/png")))
                .andExpect(status().isPayloadTooLarge());
        // The big limit is ONLY for this endpoint: everything else still refuses large bodies.
        mockMvc.perform(as(librarian, put("/api/v1/admin/books/" + bookId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "x".repeat(100_000) + "\",\"author\":\"A\",\"totalCopies\":1}"))
                .andExpect(status().isPayloadTooLarge());

        mockMvc.perform(as(librarian, upload(bookId, png, "c.png", "image/png"))).andExpect(status().isOk());
        mockMvc.perform(as(member, delete("/api/v1/admin/books/" + bookId + "/cover"))).andExpect(status().isForbidden());
        mockMvc.perform(as(librarian, delete("/api/v1/admin/books/" + bookId + "/cover"))).andExpect(status().isNoContent());
        mockMvc.perform(as(member, get("/api/v1/books/" + bookId + "/cover"))).andExpect(status().isNotFound());
        mockMvc.perform(as(member, get("/api/v1/books/" + bookId))).andExpect(jsonPath("$.coverVersion").doesNotExist());
    }

    @Test
    void aboutTheAuthorIsSavedAndShown() throws Exception {
        TestUser librarian = librarian("author-info");
        Long bookId = createBook(librarian, "Author Info Book", 1);
        mockMvc.perform(as(librarian, put("/api/v1/admin/books/" + bookId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Author Info Book","author":"Test Author","totalCopies":1,
                                 "description":"A gripping story.","aboutAuthor":"Born in 1970.\\nWrites at night."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutAuthor").value("Born in 1970.\nWrites at night."))
                .andExpect(jsonPath("$.description").value("A gripping story."));
        mockMvc.perform(as(librarian, put("/api/v1/admin/books/" + bookId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Author Info Book\",\"author\":\"Test Author\",\"totalCopies\":1,\"aboutAuthor\":\"bad\\u0000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.aboutAuthor").exists());
    }
}
