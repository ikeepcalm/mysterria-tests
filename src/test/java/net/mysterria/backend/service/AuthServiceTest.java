package net.mysterria.backend.service;

import net.mysterria.backend.dto.auth.AuthResponse;
import net.mysterria.backend.dto.auth.DiscordCallbackRequest;
import net.mysterria.backend.dto.auth.DiscordUserInfo;
import net.mysterria.backend.dto.auth.RefreshTokenRequest;
import net.mysterria.backend.entity.RefreshToken;
import net.mysterria.backend.entity.Role;
import net.mysterria.backend.entity.User;
import net.mysterria.backend.exception.AuthException;
import net.mysterria.backend.repository.RefreshTokenRepository;
import net.mysterria.backend.repository.RoleRepository;
import net.mysterria.backend.repository.UserRepository;
import net.mysterria.backend.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private DiscordService discordService;

    @InjectMocks
    private AuthService authService;

    private DiscordUserInfo discordUserInfo;
    private User testUser;
    private Role memberRole;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        discordUserInfo = TestDataFactory.createDiscordUserInfo(123456789L);
        memberRole = TestDataFactory.createMemberRole();
        testUser = TestDataFactory.createTestUser(123456789L, memberRole);
        refreshToken = TestDataFactory.createRefreshToken(testUser);
    }

    @Test
    void authenticateDiscord_NewUser_CreatesUserAndReturnsAuthResponse() {
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(discordUserInfo.id())).thenReturn(true);
        when(userRepository.findByDiscordId(discordUserInfo.id())).thenReturn(Optional.empty());
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.of(memberRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        when(jwtService.generateToken(any(UUID.class), anyMap())).thenReturn("access_token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("refresh_token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        AuthResponse response = authService.authenticateDiscord(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access_token");
        assertThat(response.refreshToken()).isEqualTo("refresh_token");

        verify(userRepository).save(argThat(user ->
                user.getDiscordId().equals(discordUserInfo.id()) &&
                user.getEmail().equals(discordUserInfo.email()) &&
                user.getRole().equals(memberRole)
        ));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void authenticateDiscord_ExistingUser_UpdatesUserAndReturnsAuthResponse() {
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(discordUserInfo.id())).thenReturn(true);
        when(userRepository.findByDiscordId(discordUserInfo.id())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any(UUID.class), anyMap())).thenReturn("access_token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("refresh_token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        AuthResponse response = authService.authenticateDiscord(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access_token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(discordUserInfo.email());
        assertThat(savedUser.getLastActivityAt()).isNotNull();
    }

    @Test
    void authenticateDiscord_NotGuildMember_ThrowsAuthException() {
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(discordUserInfo.id())).thenReturn(false);

        assertThatThrownBy(() -> authService.authenticateDiscord(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("must be a member of the Mysterria Discord server");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticateDiscord_BannedUser_ThrowsAuthException() {
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");
        User bannedUser = TestDataFactory.createBannedUser(123456789L);

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(discordUserInfo.id())).thenReturn(true);
        when(userRepository.findByDiscordId(discordUserInfo.id())).thenReturn(Optional.of(bannedUser));

        assertThatThrownBy(() -> authService.authenticateDiscord(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("banned");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void authenticateDiscord_MissingDefaultRole_ThrowsRuntimeException() {
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(discordUserInfo.id())).thenReturn(true);
        when(userRepository.findByDiscordId(discordUserInfo.id())).thenReturn(Optional.empty());
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticateDiscord(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Default role not found");
    }

    @Test
    void refreshToken_ValidToken_ReturnsNewAuthResponse() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");

        when(refreshTokenRepository.findByToken("valid_refresh_token")).thenReturn(Optional.of(refreshToken));
        when(jwtService.generateToken(any(UUID.class), anyMap())).thenReturn("new_access_token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("new_refresh_token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        AuthResponse response = authService.refreshToken(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new_access_token");
        assertThat(response.refreshToken()).isEqualTo("new_refresh_token");

        verify(refreshTokenRepository).delete(refreshToken);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshToken_InvalidToken_ThrowsAuthException() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid_token");

        when(refreshTokenRepository.findByToken("invalid_token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid refresh token");

        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
    }

    @Test
    void refreshToken_ExpiredToken_DeletesTokenAndThrowsAuthException() {
        RefreshTokenRequest request = new RefreshTokenRequest("expired_token");
        RefreshToken expiredToken = TestDataFactory.createExpiredRefreshToken(testUser);

        when(refreshTokenRepository.findByToken("expired_token")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Refresh token has expired");

        verify(refreshTokenRepository).delete(expiredToken);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshToken_BannedUser_ThrowsAuthException() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_token");
        User bannedUser = TestDataFactory.createBannedUser(123456789L);
        RefreshToken tokenForBannedUser = TestDataFactory.createRefreshToken(bannedUser);

        when(refreshTokenRepository.findByToken("valid_token")).thenReturn(Optional.of(tokenForBannedUser));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("banned");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void logout_ValidUser_DeletesAllRefreshTokens() {
        UUID userId = testUser.getId();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        authService.logout(userId);

        verify(refreshTokenRepository).deleteByUser(testUser);
    }

    @Test
    void logout_UserNotFound_ThrowsAuthException() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout(userId))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("User not found");

        verify(refreshTokenRepository, never()).deleteByUser(any(User.class));
    }

    @Test
    void authenticateDiscord_GeneratesCorrectJwtClaims() {
        DiscordCallbackRequest request = new DiscordCallbackRequest("valid_code");

        when(discordService.exchangeCodeForUser("valid_code")).thenReturn(discordUserInfo);
        when(discordService.isGuildMember(discordUserInfo.id())).thenReturn(true);
        when(userRepository.findByDiscordId(discordUserInfo.id())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any(UUID.class), anyMap())).thenReturn("access_token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("refresh_token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        authService.authenticateDiscord(request);

        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jwtService).generateToken(eq(testUser.getId()), claimsCaptor.capture());

        Map<String, Object> claims = claimsCaptor.getValue();
        assertThat(claims).containsKey("role");
        assertThat(claims).containsKey("permissions");
        assertThat(claims).containsKey("username");
        assertThat(claims.get("role")).isEqualTo(memberRole.getName());
    }
}
