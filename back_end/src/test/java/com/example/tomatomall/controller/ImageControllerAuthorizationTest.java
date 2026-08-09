package com.example.tomatomall.controller;

import com.example.tomatomall.dto.ImageUsage;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.service.ImageService;
import com.example.tomatomall.util.TokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageControllerAuthorizationTest {

    private final ImageService imageService = mock(ImageService.class);
    private final TokenUtil tokenUtil = mock(TokenUtil.class);
    private final ImageController controller = new ImageController(imageService, tokenUtil);
    private final MockMultipartFile file = new MockMultipartFile(
            "file", "image.png", "image/png", new byte[]{1}
    );

    @ParameterizedTest
    @EnumSource(value = ImageUsage.class, names = {"PRODUCT", "ADVERTISEMENT"})
    void nonAdminCannotUploadManagedImages(ImageUsage usage) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/images");
        doThrow(TomatoException.noPermission()).when(tokenUtil).validateAdminRole(request);

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> controller.uploadImage(file, usage, request)
        );

        assertEquals("403", exception.getCode());
        verifyNoInteractions(imageService);
    }

    @Test
    void authenticatedUserCanUploadAvatarWithoutAdminRole() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/images");
        when(imageService.upload(file, ImageUsage.AVATAR)).thenReturn("https://example.invalid/avatar.png");

        assertEquals(
                "https://example.invalid/avatar.png",
                controller.uploadImage(file, ImageUsage.AVATAR, request).getData()
        );
        verify(tokenUtil, never()).validateAdminRole(request);
    }
}
