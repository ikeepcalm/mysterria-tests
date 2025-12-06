package net.mysterria.backend.util;

import net.mysterria.backend.dto.auth.DiscordUserInfo;
import net.mysterria.backend.dto.economy.CreateTransactionDto;
import net.mysterria.backend.entity.*;
import net.mysterria.backend.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public class TestDataFactory {

    public static User createTestUser(Long discordId) {
        return User.builder()
                .id(UUID.randomUUID())
                .discordId(discordId)
                .email("test" + discordId + "@example.com")
                .nickname("TestUser" + discordId)
                .balance(new BigDecimal("100.00"))
                .lang("en")
                .isBanned(false)
                .lastActivityAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static User createTestUser(Long discordId, Role role) {
        User user = createTestUser(discordId);
        user.setRole(role);
        return user;
    }

    public static User createTestUserWithBalance(Long discordId, BigDecimal balance) {
        User user = createTestUser(discordId);
        user.setBalance(balance);
        return user;
    }

    public static User createBannedUser(Long discordId) {
        User user = createTestUser(discordId);
        user.setIsBanned(true);
        return user;
    }

    public static Role createTestRole(String name, int priority) {
        return Role.builder()
                .id(new Random().nextInt(10))
                .name(name)
                .priority(priority)
                .permissions(new HashSet<>(Set.of("BASIC_PERMISSIONS")))
                .build();
    }

    public static Role createMemberRole() {
        return createTestRole("MEMBER", 0);
    }

    public static Role createModeratorRole() {
        return createTestRole("MODERATOR", 50);
    }

    public static Role createAdminRole() {
        return createTestRole("ADMIN", 100);
    }

    public static RefreshToken createRefreshToken(User user) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static RefreshToken createExpiredRefreshToken(User user) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(31))
                .build();
    }

    public static Transaction createTransaction(User user, BigDecimal amount, TransactionType type) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .user(user)
                .amount(amount)
                .type(type)
                .description("Test transaction")
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static CreateTransactionDto createTransactionDto(BigDecimal amount, TransactionType type) {
        return CreateTransactionDto.builder()
                .amount(amount)
                .type(type)
                .description("Test transaction")
                .metadata(Map.of())
                .build();
    }

    public static DiscordUserInfo createDiscordUserInfo(Long discordId) {
        return new DiscordUserInfo(
                discordId,
                "testuser" + discordId,
                "1234",
                null,
                "test" + discordId + "@example.com"
        );
    }

    public static ServiceCategory createServiceCategory(String nameEn) {
        return ServiceCategory.builder()
                .id(new Random().nextInt(10))
                .name(nameEn)
                .build();
    }

    public static net.mysterria.backend.entity.Service createService(ServiceCategory category, BigDecimal price) {
        net.mysterria.backend.entity.Service service = net.mysterria.backend.entity.Service.builder()
                .id(new Random().nextInt(10))
                .category(category)
                .price(price)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Add empty localizations collection to prevent NPE
        service.setLocalizations(new java.util.ArrayList<>());
        return service;
    }

    public static Purchase createPurchase(User user, net.mysterria.backend.entity.Service service) {
        return Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .service(service)
                .delivered(false)
                .metadata(new java.util.HashMap<>())
                .build();
    }

//    public static News createNews(String titleEn, String titleUa, boolean isPublished) {
//        return News.builder()
//                .id(UUID.randomUUID())
//                .titleEn(titleEn)
//                .titleUa(titleUa)
//                .contentEn("Content " + titleEn)
//                .contentUa("Контент " + titleUa)
//                .slug(titleEn.toLowerCase().replace(" ", "-"))
//                .isPublished(isPublished)
//                .isPinned(false)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .build();
//    }
//
//    public static Vote createVote(String playerIdentifier, String siteId) {
//        return Vote.builder()
//                .id(UUID.randomUUID())
//                .siteId(siteId)
//                .ipAddress("127.0.0.1")
//                .createdAt(LocalDateTime.now())
//                .build();
//    }
}
