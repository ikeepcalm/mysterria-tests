package net.mysterria.backend.service;

import net.mysterria.backend.config.InternalApiProperties;
import net.mysterria.backend.entity.User;
import net.mysterria.backend.entity.Vote;
import net.mysterria.backend.entity.VoteSite;
import net.mysterria.backend.exception.NotFoundException;
import net.mysterria.backend.repository.UserRepository;
import net.mysterria.backend.repository.VoteRepository;
import net.mysterria.backend.repository.VoteSiteRepository;
import net.mysterria.backend.util.TestDataFactory;
import net.mysterria.discord.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private VoteSiteRepository voteSiteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EconomyService economyService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private InternalApiProperties internalApiProperties;

    @InjectMocks
    private VoteService voteService;

    private User testUser;
    private VoteSite testSite;
    private final Integer siteId = 1;
    private final String playerIdentifier = "TestPlayer";

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUser(123456789L);
        testUser.setNickname(playerIdentifier);

        testSite = VoteSite.builder()
                .id(siteId)
                .name("TestVoteSite")
                .rewardAmount(new BigDecimal("10.00"))
                .isActive(true)
                .metadata(new HashMap<>())
                .build();
    }

    @Test
    void processVote_ValidVote_CreatesVoteAndSendsNotification() {
        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByNickname(playerIdentifier)).thenReturn(Optional.of(testUser));
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        voteService.processVote(siteId, playerIdentifier, "127.0.0.1");

        ArgumentCaptor<Vote> voteCaptor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).save(voteCaptor.capture());

        Vote capturedVote = voteCaptor.getValue();
        assertThat(capturedVote.getUser()).isEqualTo(testUser);
        assertThat(capturedVote.getSite()).isEqualTo(testSite);
        assertThat(capturedVote.getProcessed()).isTrue();

        verify(notificationService).sendVoteThankYouNotification(
                testUser.getDiscordId().toString(),
                testSite.getName(),
                testSite.getRewardAmount()
        );
    }

    @Test
    void processVote_TestPlayer_ReturnsWithoutProcessing() {
        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));

        voteService.processVote(siteId, "Test", "127.0.0.1");

        verify(voteRepository, never()).save(any(Vote.class));
        verify(notificationService, never()).sendVoteThankYouNotification(anyString(), anyString(), any(BigDecimal.class));
    }

    @Test
    void processVote_SiteNotFound_ThrowsNotFoundException() {
        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.processVote(siteId, playerIdentifier, "127.0.0.1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Vote site not found or inactive");
    }

    @Test
    void processVote_PlayerNotFound_ThrowsNotFoundException() {
        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByNickname(playerIdentifier)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.processVote(siteId, playerIdentifier, "127.0.0.1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Player not found");

        verify(voteRepository, never()).save(any(Vote.class));
    }

    @Test
    void processVote_FindsUserByMinecraftUUID() {
        UUID minecraftUuid = UUID.randomUUID();
        String uuidString = minecraftUuid.toString();

        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByMinecraftUuid(minecraftUuid)).thenReturn(Optional.of(testUser));
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        voteService.processVote(siteId, uuidString, "127.0.0.1");

        verify(userRepository).findByMinecraftUuid(minecraftUuid);
        verify(voteRepository).save(any(Vote.class));
    }

    @Test
    void processVote_FindsUserByMinecraftUUIDWithoutDashes() {
        UUID minecraftUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByMinecraftUuid(minecraftUuid)).thenReturn(Optional.of(testUser));
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        voteService.processVote(siteId, String.valueOf(minecraftUuid), "127.0.0.1");

        verify(userRepository).findByMinecraftUuid(minecraftUuid);
        verify(voteRepository).save(any(Vote.class));
    }

    @Test
    void processVote_InvalidIdentifier_ThrowsNotFoundException() {
        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByNickname(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.processVote(siteId, "NonexistentPlayer", "127.0.0.1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Player not found");
    }

    @Test
    void processVote_NotificationFails_StillCompletesSuccessfully() {
        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByNickname(playerIdentifier)).thenReturn(Optional.of(testUser));
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("Discord API error")).when(notificationService)
                .sendVoteThankYouNotification(anyString(), anyString(), any(BigDecimal.class));

        assertThatCode(() -> voteService.processVote(siteId, playerIdentifier, "127.0.0.1"))
                .doesNotThrowAnyException();

        verify(voteRepository).save(any(Vote.class));
    }

    @Test
    void processVote_ParsesIpAddress() {
        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByNickname(playerIdentifier)).thenReturn(Optional.of(testUser));
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        voteService.processVote(siteId, playerIdentifier, "192.168.1.1");

        ArgumentCaptor<Vote> voteCaptor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).save(voteCaptor.capture());

        Vote capturedVote = voteCaptor.getValue();
        assertThat(capturedVote.getIpAddress()).isNotNull();
    }

    @Test
    void processVote_InvalidIpAddress_HandlesGracefully() {
        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByNickname(playerIdentifier)).thenReturn(Optional.of(testUser));
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        voteService.processVote(siteId, playerIdentifier, "invalid_ip");

        ArgumentCaptor<Vote> voteCaptor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).save(voteCaptor.capture());

        Vote capturedVote = voteCaptor.getValue();
        assertThat(capturedVote.getIpAddress()).isNull();
    }

    @Test
    void processVote_UserWithoutDiscordId_SkipsNotification() {
        User userWithoutDiscord = TestDataFactory.createTestUser(123456789L);
        userWithoutDiscord.setDiscordId(null);
        userWithoutDiscord.setNickname(playerIdentifier);

        when(voteSiteRepository.findByIdAndIsActiveTrue(siteId)).thenReturn(Optional.of(testSite));
        when(userRepository.findByNickname(playerIdentifier)).thenReturn(Optional.of(userWithoutDiscord));
        when(voteRepository.save(any(Vote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        voteService.processVote(siteId, playerIdentifier, "127.0.0.1");

        verify(voteRepository).save(any(Vote.class));
        verify(notificationService, never()).sendVoteThankYouNotification(anyString(), anyString(), any(BigDecimal.class));
    }
}
