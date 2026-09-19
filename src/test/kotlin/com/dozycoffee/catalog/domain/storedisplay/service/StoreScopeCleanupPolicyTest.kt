package com.dozycoffee.catalog.domain.storedisplay.service

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreId
import com.dozycoffee.catalog.domain.product.model.StoreScope
import com.dozycoffee.catalog.domain.storeavailability.StoreProductAvailability
import com.dozycoffee.catalog.domain.storeavailability.StoreProductAvailabilityId
import com.dozycoffee.catalog.domain.storedisplay.model.StoreDisplaySetting
import com.dozycoffee.catalog.domain.storedisplay.model.StoreDisplaySettingId
import com.dozycoffee.catalog.fixture.displaySetting
import com.dozycoffee.catalog.fixture.inventoryAvailability
import com.dozycoffee.catalog.fixture.ownerAvailability
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("StoreScopeCleanupPolicy — 판매 범위 변경 시 정리 대상 선택")
class StoreScopeCleanupPolicyTest {
    private val productId = ProductId(1)
    private val included = StoreId(1)
    private val excluded = StoreId(2)

    @Nested
    @DisplayName("전체 판매 범위")
    inner class ToAll {
        @Test
        fun `전체로 바꾸면 아무것도 지우지 않는다`() {
            val result =
                select(
                    StoreScope.All,
                    displaySettings = listOf(displaySetting(1, included, productId), displaySetting(2, excluded, productId)),
                    availabilities =
                        listOf(
                            ownerAvailability(included, productId),
                            ownerAvailability(excluded, productId),
                            inventoryAvailability(excluded, productId),
                        ),
                )

            assertTrue(result.displaySettingIds.isEmpty())
            assertTrue(result.availabilityIds.isEmpty())
        }
    }

    @Nested
    @DisplayName("한정 판매 범위")
    inner class ToLimited {
        @Test
        fun `빠진 매장의 진열 설정과 OWNER 판매 가능 여부만 고른다`() {
            val result =
                select(
                    StoreScope.Limited(setOf(included)),
                    displaySettings = listOf(displaySetting(1, included, productId), displaySetting(2, excluded, productId)),
                    availabilities = listOf(ownerAvailability(included, productId), ownerAvailability(excluded, productId)),
                )

            assertEquals(listOf(StoreDisplaySettingId(2)), result.displaySettingIds)
            assertEquals(listOf(StoreProductAvailabilityId(excluded, productId)), result.availabilityIds)
        }

        @Test
        fun `INVENTORY 판매 가능 여부는 빠진 매장이어도 고르지 않는다`() {
            // 재고관리 서비스가 주인이라 판매 범위에서 빠져도 유지한다(요구사항 1.5).
            val result =
                select(
                    StoreScope.Limited(setOf(included)),
                    availabilities = listOf(inventoryAvailability(excluded, productId)),
                )

            assertTrue(result.availabilityIds.isEmpty())
        }

        @Test
        fun `포함된 매장의 설정은 고르지 않는다`() {
            val result =
                select(
                    StoreScope.Limited(setOf(included)),
                    displaySettings = listOf(displaySetting(1, included, productId)),
                    availabilities = listOf(ownerAvailability(included, productId)),
                )

            assertTrue(result.displaySettingIds.isEmpty())
            assertTrue(result.availabilityIds.isEmpty())
        }

        @Test
        fun `대상 매장이 비어 있으면 모든 진열 설정과 OWNER 판매 가능 여부를 고른다`() {
            val other = StoreId(3)
            val result =
                select(
                    StoreScope.Limited(emptySet()),
                    displaySettings = listOf(displaySetting(1, included, productId), displaySetting(2, excluded, productId)),
                    availabilities =
                        listOf(
                            ownerAvailability(included, productId),
                            inventoryAvailability(excluded, productId),
                            ownerAvailability(other, productId),
                        ),
                )

            assertEquals(listOf(StoreDisplaySettingId(1), StoreDisplaySettingId(2)), result.displaySettingIds)
            assertEquals(
                listOf(StoreProductAvailabilityId(included, productId), StoreProductAvailabilityId(other, productId)),
                result.availabilityIds,
            )
        }
    }

    @Nested
    @DisplayName("입력 검증")
    inner class Validation {
        @Test
        fun `다른 상품의 진열 설정이 섞이면 거부한다`() {
            assertFailsWith<IllegalArgumentException> {
                select(
                    StoreScope.All,
                    displaySettings = listOf(displaySetting(1, included, productId), displaySetting(2, excluded, ProductId(99))),
                )
            }
        }

        @Test
        fun `다른 상품의 판매 가능 여부가 섞이면 거부한다`() {
            assertFailsWith<IllegalArgumentException> {
                select(
                    StoreScope.All,
                    availabilities = listOf(ownerAvailability(included, ProductId(99))),
                )
            }
        }
    }

    private fun select(
        newScope: StoreScope,
        displaySettings: List<StoreDisplaySetting> = emptyList(),
        availabilities: List<StoreProductAvailability> = emptyList(),
    ) = StoreScopeCleanupPolicy.selectTargets(productId, newScope, displaySettings, availabilities)
}
