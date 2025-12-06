package net.mysterria.backend.service;

import net.mysterria.backend.dto.economy.CreateTransactionDto;
import net.mysterria.backend.dto.shop.*;
import net.mysterria.backend.entity.Purchase;
import net.mysterria.backend.entity.ServiceCategory;
import net.mysterria.backend.entity.ServiceLocalization;
import net.mysterria.backend.entity.User;
import net.mysterria.backend.entity.enums.TransactionType;
import net.mysterria.backend.exception.BadRequestException;
import net.mysterria.backend.exception.InsufficientBalanceException;
import net.mysterria.backend.exception.NotFoundException;
import net.mysterria.backend.repository.*;
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
class ShopServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private ServiceCategoryRepository serviceCategoryRepository;

    @Mock
    private ServiceLocalizationRepository serviceLocalizationRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EconomyService economyService;

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private DiscordWebhookService discordWebhookService;

    @InjectMocks
    private ShopService shopService;

    private User testUser;
    private net.mysterria.backend.entity.Service testService;
    private ServiceCategory testCategory;
    private UUID userId;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUserWithBalance(123456789L, new BigDecimal("500.00"));
        userId = testUser.getId();

        testCategory = TestDataFactory.createServiceCategory("VIP");
        testService = TestDataFactory.createService(testCategory, new BigDecimal("100.00"));
        testService.setName("VIP Rank");
    }

    @Test
    void getAllServices_ReturnsActiveServicesOrderedByPrice() {
        net.mysterria.backend.entity.Service service1 = TestDataFactory.createService(testCategory, new BigDecimal("50.00"));
        net.mysterria.backend.entity.Service service2 = TestDataFactory.createService(testCategory, new BigDecimal("150.00"));

        when(serviceRepository.findByIsActiveTrueOrderByPriceAsc()).thenReturn(Arrays.asList(service1, service2));

        List<ServiceDto> result = shopService.getAllServices("en");

        assertThat(result).hasSize(2);
        verify(serviceRepository).findByIsActiveTrueOrderByPriceAsc();
    }

    @Test
    void getServicesByCategoryName_ReturnsServicesFilteredByCategory() {
        net.mysterria.backend.entity.Service service1 = TestDataFactory.createService(testCategory, new BigDecimal("50.00"));

        when(serviceRepository.findByCategoryNameAndIsActiveTrueOrderByPriceAsc("VIP"))
                .thenReturn(List.of(service1));

        List<ServiceDto> result = shopService.getServicesByCategoryName("VIP", "en");

        assertThat(result).hasSize(1);
        verify(serviceRepository).findByCategoryNameAndIsActiveTrueOrderByPriceAsc("VIP");
    }

    @Test
    void getAffordableServices_ReturnsOnlyServicesUserCanAfford() {
        User poorUser = TestDataFactory.createTestUserWithBalance(123456789L, new BigDecimal("75.00"));
        UUID poorUserId = poorUser.getId();

        net.mysterria.backend.entity.Service cheapService = TestDataFactory.createService(testCategory, new BigDecimal("50.00"));
        net.mysterria.backend.entity.Service expensiveService = TestDataFactory.createService(testCategory, new BigDecimal("100.00"));

        when(userRepository.findById(poorUserId)).thenReturn(Optional.of(poorUser));
        when(serviceRepository.findAffordableServices(poorUser.getBalance()))
                .thenReturn(List.of(cheapService));

        List<ServiceDto> result = shopService.getAffordableServices(poorUserId, "en");

        assertThat(result).hasSize(1);
        verify(serviceRepository).findAffordableServices(poorUser.getBalance());
    }

    @Test
    void getAffordableServices_UserNotFound_ThrowsNotFoundException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getAffordableServices(userId, "en"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void purchaseService_ValidPurchase_CompletesSuccessfully() {
        PurchaseRequest request = new PurchaseRequest(testService.getId());
        Purchase savedPurchase = TestDataFactory.createPurchase(testUser, testService);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByIdAndIsActiveTrue(testService.getId())).thenReturn(Optional.of(testService));
        when(purchaseRepository.save(any(Purchase.class))).thenReturn(savedPurchase);

        PurchaseDto result = shopService.purchaseService(userId, request);

        assertThat(result).isNotNull();

        ArgumentCaptor<CreateTransactionDto> transactionCaptor = ArgumentCaptor.forClass(CreateTransactionDto.class);
        verify(economyService).processTransaction(eq(userId), transactionCaptor.capture());

        CreateTransactionDto capturedTransaction = transactionCaptor.getValue();
        assertThat(capturedTransaction.amount()).isEqualByComparingTo(testService.getPrice().negate());
        assertThat(capturedTransaction.type()).isEqualTo(TransactionType.PURCHASE);

        verify(deliveryService).scheduleDelivery(any(Purchase.class));
        verify(discordWebhookService).purchaseCompleted(anyString(), anyString(), anyString());
    }

    @Test
    void purchaseService_UserNotFound_ThrowsNotFoundException() {
        PurchaseRequest request = new PurchaseRequest(testService.getId());

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.purchaseService(userId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");

        verify(economyService, never()).processTransaction(any(UUID.class), any(CreateTransactionDto.class));
    }

    @Test
    void purchaseService_ServiceNotFound_ThrowsNotFoundException() {
        PurchaseRequest request = new PurchaseRequest(testService.getId());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByIdAndIsActiveTrue(testService.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.purchaseService(userId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Service not found or not available");

        verify(economyService, never()).processTransaction(any(UUID.class), any(CreateTransactionDto.class));
    }

    @Test
    void purchaseService_InsufficientBalance_ThrowsInsufficientBalanceException() {
        User poorUser = TestDataFactory.createTestUserWithBalance(123456789L, new BigDecimal("50.00"));
        UUID poorUserId = poorUser.getId();

        PurchaseRequest request = new PurchaseRequest(testService.getId());

        when(userRepository.findById(poorUserId)).thenReturn(Optional.of(poorUser));
        when(serviceRepository.findByIdAndIsActiveTrue(testService.getId())).thenReturn(Optional.of(testService));

        assertThatThrownBy(() -> shopService.purchaseService(poorUserId, request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance to purchase this service");

        verify(economyService, never()).processTransaction(any(UUID.class), any(CreateTransactionDto.class));
    }

    @Test
    void purchaseService_ServiceWithDuration_SetsExpirationDate() {
        testService.setDurationDays(30);
        PurchaseRequest request = new PurchaseRequest(testService.getId());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByIdAndIsActiveTrue(testService.getId())).thenReturn(Optional.of(testService));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shopService.purchaseService(userId, request);

        ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(purchaseCaptor.capture());

        Purchase capturedPurchase = purchaseCaptor.getValue();
        assertThat(capturedPurchase.getExpiresAt()).isNotNull();
        assertThat(capturedPurchase.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    void getUserPurchases_ReturnsPaginatedPurchases() {
        Pageable pageable = PageRequest.of(0, 10);
        Purchase purchase1 = TestDataFactory.createPurchase(testUser, testService);
        Page<Purchase> purchasePage = new PageImpl<>(List.of(purchase1), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(purchaseRepository.findByUserOrderByPurchaseDateDesc(testUser, pageable)).thenReturn(purchasePage);

        Page<PurchaseDto> result = shopService.getUserPurchases(userId, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(purchaseRepository).findByUserOrderByPurchaseDateDesc(testUser, pageable);
    }

    @Test
    void getUserPurchases_UserNotFound_ThrowsNotFoundException() {
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getUserPurchases(userId, pageable))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getUserActivePurchases_ReturnsOnlyActivePurchases() {
        Purchase activePurchase = TestDataFactory.createPurchase(testUser, testService);
        activePurchase.setExpiresAt(LocalDateTime.now().plusDays(10));

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(purchaseRepository.findActivePurchases(eq(testUser), any(LocalDateTime.class)))
                .thenReturn(List.of(activePurchase));

        List<PurchaseDto> result = shopService.getUserActivePurchases(userId);

        assertThat(result).hasSize(1);
        verify(purchaseRepository).findActivePurchases(eq(testUser), any(LocalDateTime.class));
    }

    @Test
    void getPublishedServiceContent_ValidSlug_ReturnsServiceMarkdown() {
        testService.setSlug("vip-rank");
        testService.setIsPublished(true);

        when(serviceRepository.findBySlugAndIsPublishedTrueAndIsActiveTrue("vip-rank"))
                .thenReturn(Optional.of(testService));

        ServiceMarkdownDto result = shopService.getPublishedServiceContent("vip-rank", "en");

        assertThat(result).isNotNull();
        assertThat(result.getSlug()).isEqualTo("vip-rank");
        verify(serviceRepository).findBySlugAndIsPublishedTrueAndIsActiveTrue("vip-rank");
    }

    @Test
    void getPublishedServiceContent_ServiceNotPublished_ThrowsNotFoundException() {
        when(serviceRepository.findBySlugAndIsPublishedTrueAndIsActiveTrue("unpublished-service"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getPublishedServiceContent("unpublished-service", "en"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Published service content not found");
    }

    @Test
    void createService_ValidData_CreatesServiceSuccessfully() {
        CreateServiceDto dto = mock(CreateServiceDto.class);
        when(dto.getName()).thenReturn("Premium Rank");
        when(dto.getDescription()).thenReturn("Premium rank description");
        when(dto.getCategoryId()).thenReturn(testCategory.getId());
        when(dto.getPrice()).thenReturn(new BigDecimal("200.00"));

        when(serviceRepository.existsByName("Premium Rank")).thenReturn(false);
        when(serviceCategoryRepository.findById(testCategory.getId())).thenReturn(Optional.of(testCategory));
        when(serviceRepository.save(any(net.mysterria.backend.entity.Service.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceLocalizationRepository.save(any(ServiceLocalization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceDto result = shopService.createService(dto);

        assertThat(result).isNotNull();
        verify(serviceRepository).save(any(net.mysterria.backend.entity.Service.class));
        verify(serviceLocalizationRepository, times(2)).save(any(ServiceLocalization.class));
    }

    @Test
    void createService_DuplicateName_ThrowsBadRequestException() {
        CreateServiceDto dto = mock(CreateServiceDto.class);
        when(dto.getName()).thenReturn("Existing Service");

        when(serviceRepository.existsByName("Existing Service")).thenReturn(true);

        assertThatThrownBy(() -> shopService.createService(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Service with this name already exists");

        verify(serviceRepository, never()).save(any(net.mysterria.backend.entity.Service.class));
    }

    @Test
    void createService_CategoryNotFound_ThrowsNotFoundException() {
        CreateServiceDto dto = mock(CreateServiceDto.class);
        when(dto.getName()).thenReturn("New Service");
        when(dto.getCategoryId()).thenReturn(999);

        when(serviceRepository.existsByName("New Service")).thenReturn(false);
        when(serviceCategoryRepository.findById(anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.createService(dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Service category not found");

        verify(serviceRepository, never()).save(any(net.mysterria.backend.entity.Service.class));
    }
}
