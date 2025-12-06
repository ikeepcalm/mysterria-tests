package net.mysterria.backend.integration;

import net.mysterria.backend.dto.auth.AuthResponse;
import net.mysterria.backend.dto.auth.DiscordCallbackRequest;
import net.mysterria.backend.dto.auth.DiscordUserInfo;
import net.mysterria.backend.dto.auth.RefreshTokenRequest;
import net.mysterria.backend.entity.RefreshToken;
import net.mysterria.backend.repository.RefreshTokenRepository;
import net.mysterria.backend.service.DiscordService;
import net.mysterria.backend.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends BaseHttpIntegrationTest {

    @MockBean
    private DiscordService discordService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void discordCallback_NewUser_CreatesUserAndReturnsTokens() throws Exception {
        DiscordUserInfo discordUserInfo = TestDataFactory.createDiscordUserInfo(987654321L);
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(987654321L)).thenReturn(true);

        mockMvc.perform(post("/api/auth/discord/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void discordCallback_ExistingUser_UpdatesAndReturnsTokens() throws Exception {
        DiscordUserInfo discordUserInfo = TestDataFactory.createDiscordUserInfo(testUser.getDiscordId());
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(testUser.getDiscordId())).thenReturn(true);

        mockMvc.perform(post("/api/auth/discord/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.userId").value(testUser.getId().toString()));
    }

    @Test
    void discordCallback_NotGuildMember_ReturnsUnauthorized() throws Exception {
        DiscordUserInfo discordUserInfo = TestDataFactory.createDiscordUserInfo(999999999L);
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(999999999L)).thenReturn(false);

        mockMvc.perform(post("/api/auth/discord/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_ValidToken_ReturnsNewTokens() throws Exception {
        RefreshToken refreshToken = TestDataFactory.createRefreshToken(testUser);
        refreshToken = refreshTokenRepository.save(refreshToken);

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken.getToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void refreshToken_InvalidToken_ReturnsUnauthorized() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid_token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_ExpiredToken_ReturnsUnauthorized() throws Exception {
        RefreshToken refreshToken = TestDataFactory.createExpiredRefreshToken(testUser);
        refreshToken = refreshTokenRepository.save(refreshToken);

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken.getToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_AuthenticatedUser_ClearsCookies() throws Exception {
        mockMvc.perform(delete("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));
    }
}
