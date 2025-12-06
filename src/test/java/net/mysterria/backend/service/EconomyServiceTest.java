package net.mysterria.backend.service;

import net.mysterria.backend.dto.economy.*;
import net.mysterria.backend.entity.Transaction;
import net.mysterria.backend.entity.User;
import net.mysterria.backend.entity.enums.TransactionType;
import net.mysterria.backend.exception.BadRequestException;
import net.mysterria.backend.exception.InsufficientBalanceException;
import net.mysterria.backend.exception.NotFoundException;
import net.mysterria.backend.repository.TransactionRepository;
import net.mysterria.backend.repository.UserRepository;
import net.mysterria.backend.util.TestDataFactory;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EconomyServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DiscordWebhookService discordWebhookService;

    @InjectMocks
    private EconomyService economyService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUserWithBalance(123456789L, new BigDecimal("100.00"));
        userId = testUser.getId();
    }

    @Test
    void processTransaction_ValidDeposit_IncreasesBalanceAndCreatesTransaction() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("50.00"),
                TransactionType.REWARD
        );

        Transaction savedTransaction = TestDataFactory.createTransaction(testUser, dto.amount(), dto.type());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.updateBalance(eq(userId), eq(dto.amount()))).thenReturn(1);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionRepository.countRecentTransactions(eq(testUser), any(LocalDateTime.class))).thenReturn(0L);

        TransactionDto result = economyService.processTransaction(userId, dto);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(result.type()).isEqualTo(TransactionType.REWARD);

        verify(userRepository).updateBalance(userId, dto.amount());
        verify(transactionRepository).save(any(Transaction.class));
        verify(discordWebhookService, never()).largeTransaction(anyString(), anyString(), anyString());
    }

    @Test
    void processTransaction_ValidWithdrawal_DecreasesBalanceAndCreatesTransaction() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("-30.00"),
                TransactionType.PURCHASE
        );

        Transaction savedTransaction = TestDataFactory.createTransaction(testUser, dto.amount(), dto.type());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.updateBalance(eq(userId), eq(dto.amount()))).thenReturn(1);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionRepository.countRecentTransactions(eq(testUser), any(LocalDateTime.class))).thenReturn(0L);

        TransactionDto result = economyService.processTransaction(userId, dto);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("-30.00"));
        assertThat(result.type()).isEqualTo(TransactionType.PURCHASE);

        verify(userRepository).updateBalance(userId, dto.amount());
    }

    @Test
    void processTransaction_InsufficientBalance_ThrowsInsufficientBalanceException() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("-150.00"),
                TransactionType.PURCHASE
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(transactionRepository.countRecentTransactions(eq(testUser), any(LocalDateTime.class))).thenReturn(0L);

        assertThatThrownBy(() -> economyService.processTransaction(userId, dto))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");

        verify(userRepository, never()).updateBalance(any(UUID.class), any(BigDecimal.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void processTransaction_ExceedsMaxBalance_ThrowsBadRequestException() {
        User richUser = TestDataFactory.createTestUserWithBalance(123456789L, new BigDecimal("999000.00"));

        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("2000.00"),
                TransactionType.REWARD
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(richUser));
        when(transactionRepository.countRecentTransactions(eq(richUser), any(LocalDateTime.class))).thenReturn(0L);

        assertThatThrownBy(() -> economyService.processTransaction(userId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Balance would exceed maximum allowed");

        verify(userRepository, never()).updateBalance(any(UUID.class), any(BigDecimal.class));
    }

    @Test
    void processTransaction_ZeroAmount_ThrowsBadRequestException() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                BigDecimal.ZERO,
                TransactionType.REWARD
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> economyService.processTransaction(userId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Transaction amount cannot be zero");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void processTransaction_PenaltyWithPositiveAmount_ThrowsBadRequestException() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("10.00"),
                TransactionType.PENALTY
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> economyService.processTransaction(userId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Penalty must be negative amount");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void processTransaction_TooManyRecentTransactions_ThrowsBadRequestException() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("10.00"),
                TransactionType.REWARD
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(transactionRepository.countRecentTransactions(eq(testUser), any(LocalDateTime.class))).thenReturn(5L);

        assertThatThrownBy(() -> economyService.processTransaction(userId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Too many transactions");

        verify(userRepository, never()).updateBalance(any(UUID.class), any(BigDecimal.class));
    }

    @Test
    void processTransaction_LargeTransaction_TriggersWebhook() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("1500.00"),
                TransactionType.DONATION
        );

        Transaction savedTransaction = TestDataFactory.createTransaction(testUser, dto.amount(), dto.type());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.updateBalance(eq(userId), eq(dto.amount()))).thenReturn(1);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionRepository.countRecentTransactions(eq(testUser), any(LocalDateTime.class))).thenReturn(0L);

        economyService.processTransaction(userId, dto);

        verify(discordWebhookService).largeTransaction(
                eq(testUser.getNickname()),
                eq("1500.00"),
                eq(dto.description())
        );
    }

    @Test
    void processTransaction_BalanceUpdateFails_ThrowsInsufficientBalanceException() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("10.00"),
                TransactionType.REWARD
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.updateBalance(eq(userId), eq(dto.amount()))).thenReturn(0);
        when(transactionRepository.countRecentTransactions(eq(testUser), any(LocalDateTime.class))).thenReturn(0L);

        assertThatThrownBy(() -> economyService.processTransaction(userId, dto))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Failed to update balance");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void processTransaction_UserNotFound_ThrowsNotFoundException() {
        CreateTransactionDto dto = TestDataFactory.createTransactionDto(
                new BigDecimal("10.00"),
                TransactionType.REWARD
        );

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> economyService.processTransaction(userId, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getBalance_ValidUser_ReturnsBalanceDto() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        BalanceDto result = economyService.getBalance(userId);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.balance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.lastUpdated()).isNotNull();
    }

    @Test
    void getBalance_UserNotFound_ThrowsNotFoundException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> economyService.getBalance(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getTransactionHistory_WithoutTypeFilter_ReturnsAllTransactions() {
        TransactionHistoryRequest request = new TransactionHistoryRequest(null);
        Pageable pageable = PageRequest.of(0, 10);

        List<Transaction> transactions = Arrays.asList(
                TestDataFactory.createTransaction(testUser, new BigDecimal("50.00"), TransactionType.REWARD),
                TestDataFactory.createTransaction(testUser, new BigDecimal("-20.00"), TransactionType.PURCHASE)
        );
        Page<Transaction> transactionPage = new PageImpl<>(transactions, pageable, transactions.size());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(transactionRepository.findByUserOrderByCreatedAtDesc(testUser, pageable)).thenReturn(transactionPage);

        Page<TransactionDto> result = economyService.getTransactionHistory(userId, request, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);

        verify(transactionRepository).findByUserOrderByCreatedAtDesc(testUser, pageable);
        verify(transactionRepository, never()).findByUserAndTypeOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void getTransactionHistory_WithTypeFilter_ReturnsFilteredTransactions() {
        TransactionHistoryRequest request = new TransactionHistoryRequest(TransactionType.REWARD);
        Pageable pageable = PageRequest.of(0, 10);

        List<Transaction> transactions = List.of(
                TestDataFactory.createTransaction(testUser, new BigDecimal("50.00"), TransactionType.REWARD)
        );
        Page<Transaction> transactionPage = new PageImpl<>(transactions, pageable, transactions.size());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(transactionRepository.findByUserAndTypeOrderByCreatedAtDesc(testUser, TransactionType.REWARD, pageable))
                .thenReturn(transactionPage);

        Page<TransactionDto> result = economyService.getTransactionHistory(userId, request, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().type()).isEqualTo(TransactionType.REWARD);

        verify(transactionRepository).findByUserAndTypeOrderByCreatedAtDesc(testUser, TransactionType.REWARD, pageable);
    }

    @Test
    void getTransactionHistory_UserNotFound_ThrowsNotFoundException() {
        TransactionHistoryRequest request = new TransactionHistoryRequest(null);
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> economyService.getTransactionHistory(userId, request, pageable))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void adminAdjustBalance_ValidRequest_CreatesAdminAdjustTransaction() {
        UUID adminId = UUID.randomUUID();
        AdminBalanceAdjustDto dto = new AdminBalanceAdjustDto();
        dto.setUserId(userId);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setReason("Admin bonus");
        dto.setAdminId(adminId);

        Transaction savedTransaction = TestDataFactory.createTransaction(testUser, dto.getAmount(), TransactionType.ADMIN_ADJUST);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.updateBalance(eq(userId), eq(dto.getAmount()))).thenReturn(1);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionRepository.countRecentTransactions(eq(testUser), any(LocalDateTime.class))).thenReturn(0L);

        TransactionDto result = economyService.adminAdjustBalance(dto);

        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(TransactionType.ADMIN_ADJUST);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        Transaction capturedTransaction = transactionCaptor.getValue();
        assertThat(capturedTransaction.getType()).isEqualTo(TransactionType.ADMIN_ADJUST);
        assertThat(capturedTransaction.getDescription()).isEqualTo("Admin bonus");
    }

    @Test
    void adminAdjustBalance_UserNotFound_ThrowsNotFoundException() {
        UUID adminId = UUID.randomUUID();
        AdminBalanceAdjustDto dto = new AdminBalanceAdjustDto();
        dto.setUserId(userId);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setReason("Admin bonus");
        dto.setAdminId(adminId);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> economyService.adminAdjustBalance(dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
