package com.starforge.app.game.avatar

import androidx.compose.ui.graphics.Color
import com.starforge.app.ui.theme.Coral
import com.starforge.app.ui.theme.Gold
import com.starforge.app.ui.theme.Purple
import com.starforge.app.ui.theme.Teal

/**
 * Portuguese cultural symbols, hand-drawn as sticker-style vector icons (see [SymbolIcon]).
 * Each has a cyclic background colour for the avatar circle. `id` is what we persist at
 * `/jogadores/{uid}/avatar`.
 */
enum class PortugueseSymbol(val id: String, val displayName: String, val bg: Color) {
    AZULEJO("azulejo", "Azulejo", Purple),
    NATA("nata", "Pastel de Nata", Gold),
    CARAVELA("caravela", "Caravela", Teal),
    FAROL("farol", "Farol", Coral),
    SARDINHA("sardinha", "Sardinha", Purple),
    GALO("galo", "Galo de Barcelos", Gold),
    LUSIADAS("lusiadas", "Os Lusíadas", Teal),
    GUITARRA("guitarra", "Guitarra", Coral),
    CALCADA("calcada", "Calçada", Purple),
    CORACAO("coracao", "Coração de Viana", Coral);

    companion object {
        fun fromId(id: String?): PortugueseSymbol? = entries.firstOrNull { it.id == id }
    }
}
