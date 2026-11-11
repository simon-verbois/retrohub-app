package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.verbois.retrohub.ui.onboarding.FieldColors
import ovh.verbois.retrohub.ui.theme.Palette

/** Un champ du formulaire d’une boîte de dialogue. */
data class DialogField(val label: String, val initial: String = "", val secret: Boolean = false, val digits: Boolean = false, val singleLine: Boolean = true)

/**
 * Boîte de dialogue à champs (code parental, mot de passe, code de triche…). [onConfirm] reçoit
 * les valeurs et renvoie un message d’erreur à afficher, ou null si c’est bon.
 */
@Composable
fun FormDialog(title: String, text: String?, fields: List<DialogField>, confirm: String, onConfirm: suspend (List<String>) -> String?, onDismiss: () -> Unit) {
    val values = remember { fields.map { mutableStateOf(it.initial) } }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var submit by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(submit) {
        if (submit == 0) return@LaunchedEffect
        busy = true
        error = onConfirm(values.map { it.value })
        busy = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.widthIn(max = 460.dp).padding(24.dp)) {
            Column(
                Modifier.background(Palette.SkyLow).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim) }
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        values[index].value,
                        { values[index].value = if (field.digits) it.filter(Char::isDigit).take(8) else it },
                        Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                        label = { Text(field.label) },
                        singleLine = field.singleLine,
                        visualTransformation = if (field.secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                            when {
                                field.digits -> KeyboardType.NumberPassword
                                field.secret -> KeyboardType.Password
                                else -> KeyboardType.Text
                            },
                            imeAction = if (index == fields.lastIndex) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onDone = { if (!busy) submit++ }),
                        colors = FieldColors(),
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger) }
                ActionButtons(confirm, { if (!busy) submit++ }, Modifier.padding(top = 4.dp), secondary = "Annuler", onSecondary = onDismiss, loading = busy)
            }
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { first.requestFocus() }
    }
}

/** Demande le code parental ; [onUnlocked] est appelé quand il est bon. */
@Composable
fun PinDialog(hash: String, reason: String, onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    FormDialog(
        "Code parental",
        reason,
        listOf(DialogField("Code", secret = true, digits = true)),
        "Déverrouiller",
        onConfirm = { (pin) ->
            if (ovh.verbois.retrohub.core.Pin.matches(pin, hash)) {
                onUnlocked()
                null
            } else {
                "Ce n’est pas le bon code."
            }
        },
        onDismiss = onDismiss,
    )
}
