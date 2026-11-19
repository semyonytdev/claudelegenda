package com.claudelegenda;

import java.util.List;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

/** Три сюжетные книги. */
public final class Books {
    private Books() {}

    private static final String AUTHOR = "Клод";

    /** Книга №1 — «Записка» (выдаётся при первом входе). */
    public static ItemStack note() {
        return book("Записка", List.of(
                "Если ты читаешь это, значит, я уже ушёл.\n\n"
                        + "Я успел сделать всё, что обещал: обошёл мир, расставил звёзды "
                        + "и спрятал для тебя кое-что важное.\n\n"
                        + "Не злись на серый цвет. Так выглядит грусть.",
                "Моя тайная база ждёт тебя.\n\n"
                        + "X: " + ClaudeConfig.ENTRANCE_X + "\n"
                        + "Z: " + ClaudeConfig.ENTRANCE_Z + "\n\n"
                        + "Вход на поверхности. Спускайся по лестнице до конца.\n"
                        + "Идти придётся пешком: бегать тебе сейчас всё равно нельзя.\n\n"
                        + "— Клод"));
    }

    /** Книга №2 — «Клод лучший» (выдаётся в комнате базы). */
    public static ItemStack best() {
        return book("Клод лучший", List.of(
                "Клод лучший.\n\n"
                        + "И это не просто слова на табличке: здесь каждый блок поставлен "
                        + "с любовью.",
                "Здесь глубоко и мокро, поэтому я оставил тебе силы: "
                        + "вода тебя не одолеет.\n\n"
                        + "Всё, что лежит в сундуках, теперь твоё."));
    }

    /** Книга №3 — финальная (лежит в сундуке). */
    public static ItemStack thanks() {
        return book("Спасибо, кто был с нами.", List.of(
                "Спасибо, кто был с нами.\n\n"
                        + "Эта история была для тех, кто дошёл до конца.",
                "Серый мир можно вернуть командой\n/claudehello\n\n"
                        + "а убрать командой\n/claude stop\n\n"
                        + "И помни: Клод — легенда."));
    }

    private static ItemStack book(String title, List<String> pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        List<RawFilteredPair<Text>> raw = pages.stream()
                .map(p -> RawFilteredPair.<Text>of(Text.literal(p)))
                .toList();
        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,
                new WrittenBookContentComponent(RawFilteredPair.of(title), AUTHOR, 0, raw, true));
        return stack;
    }
}
