package com.example.salarycalc_trackincomeandexpense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex

// Renders the options as an inline list right below the field instead of a
// popup. Popup DropdownMenus misposition inside scrollable Columns (options
// never appear on some devices), so this inline version always shows options.
@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = "$selected ▼",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    Text(
                        text = option,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (option == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(option)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// Text field with autocomplete suggestions shown as a dropdown overlay below
// it — the dropdown floats over content without pushing anything around.
@Composable
fun SuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var fieldHeight by remember { mutableStateOf(0) }
    var fieldOffset by remember { mutableStateOf(IntOffset.Zero) }
    var fieldWidth by remember { mutableStateOf(0) }

    Box(modifier) {
        FormTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardType = keyboardType,
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    fieldOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                    fieldHeight = coords.size.height
                    fieldWidth = coords.size.width
                }
                .then(
                    if (onFocusChanged != null)
                        Modifier.onFocusChanged { onFocusChanged(it.isFocused) }
                    else Modifier
                )
        )
        if (suggestions.isNotEmpty()) {
            Popup(
                offset = IntOffset(0, fieldHeight),
                properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
            ) {
                Column(
                    modifier = Modifier
                        .width(with(androidx.compose.ui.platform.LocalDensity.current) { fieldWidth.toDp() })
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    suggestions.take(8).forEach { s ->
                        Text(
                            text = s,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(s) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

// Inline autocomplete suggestions for the Source/Category text fields. Rendered
// directly below the field (not a popup) so it always shows while typing on a
// scrollable form. Manual text entry still works — the field is free text.
@Composable
fun SuggestionList(
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .heightIn(max = 160.dp)
            .verticalScroll(rememberScrollState())
    ) {
        suggestions.forEach { s ->
            Text(
                text = s,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(s) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}
