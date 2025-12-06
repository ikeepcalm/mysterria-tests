package net.mysterria.backend.service;

import net.mysterria.backend.dto.admin.CreatePunishmentDto;
import net.mysterria.backend.dto.admin.PunishmentDto;
import net.mysterria.backend.entity.Punishment;
import net.mysterria.backend.entity.Role;
import net.mysterria.backend.entity.User;
import net.mysterria.backend.entity.enums.PunishmentType;
import net.mysterria.backend.exception.BadRequestException;
import net.mysterria.backend.exception.NotFoundException;
import net.mysterria.backend.repository.PunishmentRepository;
import net.mysterria.backend.repository.RoleRepository;
import net.mysterria.backend.repository.UserRepository;
import net.mysterria.backend.util.TestDataFactory;
import net.mysterria.discord.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PunishmentServiceTest {

    @Mock
    private PunishmentRepository punishmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private DiscordWebhookService discordWebhookService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PunishmentService punishmentService;

    private User testUser;
    private User moderator;
    private Role memberRole;
    private Role moderatorRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        memberRole = TestDataFactory.createTestRole("MEMBER", 0);
        moderatorRole = TestDataFactory.createTestRole("MODERATOR", 50);
        adminRole = TestDataFactory.createTestRole("ADMIN", 100);

        testUser = TestDataFactory.createTestUser(123456789L, memberRole);
        moderator = TestDataFactory.createTestUser(987654321L, moderatorRole);
    }

    @Test
    void createPunishment_ValidWarn_CreatesPunishmentAndIncrementsCount() {
        CreatePunishmentDto dto = new CreatePunishmentDto(
                testUser.getId(),
                PunishmentType.WARN,
                "Breaking server rules",
                null
        );

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(punishmentRepository.save(any(Punishment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(punishmentRepository.countByUserAndTypeAndIsActiveTrue(testUser, PunishmentType.WARN)).thenReturn(1L);

        PunishmentDto result = punishmentService.createPunishment(moderator.getId(), dto);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(PunishmentType.WARN);

        verify(punishmentRepository).save(any(Punishment.class));
        verify(userRepository).save(argThat(user ->
                user.getPunishmentCount() == 1
        ));
        verify(auditService).logAction(
                eq(moderator.getId()),
                eq(testUser.getId()),
                eq("PUNISHMENT_CREATED"),
                anyMap(),
                anyMap(),
                eq("Breaking server rules"),
                eq("system")
        );
    }

    @Test
    void createPunishment_Ban_BansUserAndNotifiesDiscord() {
        CreatePunishmentDto dto = new CreatePunishmentDto(
                testUser.getId(),
                PunishmentType.BAN,
                "Severe violations",
                null
        );

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(punishmentRepository.save(any(Punishment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.of(memberRole));

        PunishmentDto result = punishmentService.createPunishment(moderator.getId(), dto);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(PunishmentType.BAN);

        verify(userRepository).save(argThat(User::getIsBanned));
        verify(discordWebhookService).userBanned(
                eq(testUser.getNickname()),
                eq("Severe violations"),
                eq(moderator.getNickname())
        );
    }

    @Test
    void createPunishment_ModeratorPunishesHigherRole_ThrowsBadRequestException() {
        User admin = TestDataFactory.createTestUser(111111111L, adminRole);

        CreatePunishmentDto dto = new CreatePunishmentDto(
                admin.getId(),
                PunishmentType.WARN,
                "Test",
                null
        );

        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));

        assertThatThrownBy(() -> punishmentService.createPunishment(moderator.getId(), dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot punish user with equal or higher role");

        verify(punishmentRepository, never()).save(any(Punishment.class));
    }

    @Test
    void createPunishment_ModeratorPunishesEqualRole_ThrowsBadRequestException() {
        User anotherModerator = TestDataFactory.createTestUser(222222222L, moderatorRole);

        CreatePunishmentDto dto = new CreatePunishmentDto(
                anotherModerator.getId(),
                PunishmentType.WARN,
                "Test",
                null
        );

        when(userRepository.findById(anotherModerator.getId())).thenReturn(Optional.of(anotherModerator));
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));

        assertThatThrownBy(() -> punishmentService.createPunishment(moderator.getId(), dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot punish user with equal or higher role");

        verify(punishmentRepository, never()).save(any(Punishment.class));
    }

    @Test
    void createPunishment_UserNotFound_ThrowsNotFoundException() {
        CreatePunishmentDto dto = new CreatePunishmentDto(
                UUID.randomUUID(),
                PunishmentType.WARN,
                "Test",
                null
        );

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> punishmentService.createPunishment(moderator.getId(), dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void createPunishment_ThirdWarning_CreatesAutomaticMute() {
        CreatePunishmentDto dto = new CreatePunishmentDto(
                testUser.getId(),
                PunishmentType.WARN,
                "Rule violation",
                null
        );

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(punishmentRepository.countByUserAndTypeAndIsActiveTrue(testUser, PunishmentType.WARN)).thenReturn(3L);
        when(punishmentRepository.save(any(Punishment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        punishmentService.createPunishment(moderator.getId(), dto);

        ArgumentCaptor<Punishment> punishmentCaptor = ArgumentCaptor.forClass(Punishment.class);
        verify(punishmentRepository, times(2)).save(punishmentCaptor.capture());

        List<Punishment> savedPunishments = punishmentCaptor.getAllValues();
        assertThat(savedPunishments).hasSize(2);
        assertThat(savedPunishments.get(1).getType()).isEqualTo(PunishmentType.MUTE);
        assertThat(savedPunishments.get(1).getReason()).contains("Automatic mute: 3rd warning");
    }

    @Test
    void createPunishment_EighthWarning_CreatesAutomaticBan() {
        CreatePunishmentDto dto = new CreatePunishmentDto(
                testUser.getId(),
                PunishmentType.WARN,
                "Rule violation",
                null
        );

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(punishmentRepository.countByUserAndTypeAndIsActiveTrue(testUser, PunishmentType.WARN)).thenReturn(8L);
        when(punishmentRepository.save(any(Punishment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.of(memberRole));

        punishmentService.createPunishment(moderator.getId(), dto);

        ArgumentCaptor<Punishment> punishmentCaptor = ArgumentCaptor.forClass(Punishment.class);
        verify(punishmentRepository, times(2)).save(punishmentCaptor.capture());

        List<Punishment> savedPunishments = punishmentCaptor.getAllValues();
        assertThat(savedPunishments).hasSize(2);
        assertThat(savedPunishments.get(1).getType()).isEqualTo(PunishmentType.BAN);
        assertThat(savedPunishments.get(1).getReason()).contains("Automatic ban: 8th warning");

        verify(userRepository).save(argThat(User::getIsBanned));
    }

    @Test
    void removePunishment_ValidPunishment_DeactivatesPunishment() {
        Punishment punishment = Punishment.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .moderator(moderator)
                .type(PunishmentType.WARN)
                .reason("Test")
                .isActive(true)
                .build();

        when(punishmentRepository.findById(punishment.getId())).thenReturn(Optional.of(punishment));
        when(punishmentRepository.save(any(Punishment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        punishmentService.removePunishment(punishment.getId(), moderator.getId());

        verify(punishmentRepository).save(argThat(p -> !p.getIsActive()));
        verify(auditService).logAction(
                eq(moderator.getId()),
                eq(testUser.getId()),
                eq("PUNISHMENT_REMOVED"),
                anyMap(),
                anyMap(),
                eq("Punishment removed"),
                eq("system")
        );
    }

    @Test
    void removePunishment_BanRemoval_UnbansUser() {
        testUser.setIsBanned(true);
        Punishment banPunishment = Punishment.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .moderator(moderator)
                .type(PunishmentType.BAN)
                .reason("Test ban")
                .isActive(true)
                .build();

        when(punishmentRepository.findById(banPunishment.getId())).thenReturn(Optional.of(banPunishment));
        when(punishmentRepository.save(any(Punishment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        punishmentService.removePunishment(banPunishment.getId(), moderator.getId());

        verify(userRepository).save(argThat(user -> !user.getIsBanned()));
    }

    @Test
    void removePunishment_PunishmentNotFound_ThrowsNotFoundException() {
        UUID punishmentId = UUID.randomUUID();

        when(punishmentRepository.findById(punishmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> punishmentService.removePunishment(punishmentId, moderator.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Punishment not found");
    }

    @Test
    void getPunishments_WithUserId_ReturnsUserPunishments() {
        Pageable pageable = PageRequest.of(0, 10);
        Punishment punishment = Punishment.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .moderator(moderator)
                .type(PunishmentType.WARN)
                .reason("Test")
                .build();

        Page<Punishment> punishmentPage = new PageImpl<>(List.of(punishment), pageable, 1);

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(punishmentRepository.findByUserOrderByCreatedAtDesc(testUser, pageable)).thenReturn(punishmentPage);

        Page<PunishmentDto> result = punishmentService.getPunishments(testUser.getId(), pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(punishmentRepository).findByUserOrderByCreatedAtDesc(testUser, pageable);
    }

    @Test
    void getPunishments_WithoutUserId_ReturnsAllPunishments() {
        Pageable pageable = PageRequest.of(0, 10);
        Punishment punishment = Punishment.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .moderator(moderator)
                .type(PunishmentType.WARN)
                .reason("Test")
                .build();

        Page<Punishment> punishmentPage = new PageImpl<>(List.of(punishment), pageable, 1);

        when(punishmentRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(punishmentPage);

        Page<PunishmentDto> result = punishmentService.getPunishments(null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(punishmentRepository).findAllByOrderByCreatedAtDesc(pageable);
    }

    @Test
    void removeWarning_HasActiveWarnings_RemovesMostRecent() {
        Punishment warning1 = Punishment.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .type(PunishmentType.WARN)
                .isActive(true)
                .build();

        testUser.setPunishmentCount(2);

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(punishmentRepository.findByUserAndTypeAndIsActiveTrueOrderByCreatedAtDesc(testUser, PunishmentType.WARN))
                .thenReturn(List.of(warning1));
        when(punishmentRepository.save(any(Punishment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = punishmentService.removeWarning(testUser.getId(), moderator.getId(), "Good behavior");

        assertThat(result).isTrue();
        verify(punishmentRepository).save(argThat(p -> !p.getIsActive()));
        verify(userRepository).save(argThat(user -> user.getPunishmentCount() == 1));
        verify(auditService).logAction(
                eq(moderator.getId()),
                eq(testUser.getId()),
                eq("WARNING_REMOVED"),
                anyMap(),
                anyMap(),
                eq("Good behavior"),
                eq("system")
        );
    }

    @Test
    void removeWarning_NoActiveWarnings_ReturnsFalse() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(punishmentRepository.findByUserAndTypeAndIsActiveTrueOrderByCreatedAtDesc(testUser, PunishmentType.WARN))
                .thenReturn(Collections.emptyList());

        boolean result = punishmentService.removeWarning(testUser.getId(), moderator.getId(), "Good behavior");

        assertThat(result).isFalse();
        verify(punishmentRepository, never()).save(any(Punishment.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserWarnCount_ReturnsCorrectCount() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(punishmentRepository.countByUserAndTypeAndIsActiveTrue(testUser, PunishmentType.WARN)).thenReturn(5L);

        long count = punishmentService.getUserWarnCount(testUser.getId());

        assertThat(count).isEqualTo(5L);
    }
}
