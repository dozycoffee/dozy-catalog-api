package com.dozycoffee.catalog.domain.optiongroup

import com.dozycoffee.catalog.domain.optiongroup.exception.DuplicateOptionKeyException
import com.dozycoffee.catalog.domain.optiongroup.exception.EmptyOptionGroupException
import com.dozycoffee.catalog.domain.shared.Money
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("OptionGroup")
class OptionGroupTest {
    @Nested
    @DisplayName("신규 생성")
    inner class Creation {
        @Test
        fun `옵션이 하나도 없으면 생성할 수 없다`() {
            assertFailsWith<EmptyOptionGroupException> {
                OptionGroup.NewOptionGroup.of(
                    name = "사이즈",
                    selectionType = SelectionType.SINGLE,
                    required = true,
                    options = emptyList(),
                )
            }
        }

        @Test
        fun `그룹 안에 optionKey가 중복되면 생성할 수 없다`() {
            val exception =
                assertFailsWith<DuplicateOptionKeyException> {
                    OptionGroup.NewOptionGroup.of(
                        name = "사이즈",
                        selectionType = SelectionType.SINGLE,
                        required = true,
                        options = listOf(option("TALL", 0), option("TALL", 500)),
                    )
                }

            assertEquals("DUPLICATE_OPTION_KEY", exception.code)
        }

        @Test
        fun `검증을 통과하면 입력한 옵션 순서 그대로 보관한다`() {
            val newGroup =
                OptionGroup.NewOptionGroup.of(
                    name = "사이즈",
                    selectionType = SelectionType.SINGLE,
                    required = true,
                    options = listOf(option("TALL", 0), option("GRANDE", 500)),
                )

            assertEquals(listOf(OptionKey("TALL"), OptionKey("GRANDE")), newGroup.options.map { it.optionKey })
        }
    }

    @Nested
    @DisplayName("옵션 목록 전체 교체")
    inner class ReplaceOptions {
        @Test
        fun `옵션을 0개로 만들 수 없다`() {
            val group = optionGroup(option("TALL", 0))

            assertFailsWith<EmptyOptionGroupException> { group.replaceOptions(emptyList()) }
        }

        @Test
        fun `거부된 교체는 기존 옵션을 남겨둔다`() {
            val group = optionGroup(option("TALL", 0))

            runCatching { group.replaceOptions(emptyList()) }

            assertEquals(listOf(OptionKey("TALL")), group.options.map { it.optionKey })
        }

        @Test
        fun `중복 optionKey로는 교체할 수 없다`() {
            val group = optionGroup(option("TALL", 0))

            assertFailsWith<DuplicateOptionKeyException> {
                group.replaceOptions(listOf(option("GRANDE", 500), option("GRANDE", 700)))
            }
        }

        @Test
        fun `교체하면 입력한 목록과 순서로 통째로 바뀐다`() {
            val group = optionGroup(option("TALL", 0))

            group.replaceOptions(listOf(option("GRANDE", 500), option("VENTI", 1000)))

            assertEquals(listOf(OptionKey("GRANDE"), OptionKey("VENTI")), group.options.map { it.optionKey })
        }
    }

    @Nested
    @DisplayName("즉시 반영 필드")
    inner class ImmediateFields {
        @Test
        fun `이름 선택방식 필수여부는 즉시 반영된다`() {
            val group = optionGroup(option("TALL", 0))

            group.rename("컵 사이즈")
            group.changeSelectionType(SelectionType.MULTI)
            group.changeRequired(false)

            assertEquals("컵 사이즈", group.name)
            assertEquals(SelectionType.MULTI, group.selectionType)
            assertEquals(false, group.required)
        }
    }

    private fun option(
        key: String,
        price: Long,
    ) = Option(OptionKey(key), name = key, price = Money(price))

    private fun optionGroup(vararg options: Option) =
        OptionGroup(
            id = OptionGroupId(1),
            name = "사이즈",
            selectionType = SelectionType.SINGLE,
            required = true,
            options = options.toList(),
        )
}
