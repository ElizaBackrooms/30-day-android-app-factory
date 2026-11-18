package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalCard
import com.example.ui.theme.TextMuted

@Composable
fun TacticalCard(
  modifier: Modifier = Modifier,
  borderColor: Color = TacticalBorder,
  containerColor: Color = TacticalCard,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

@Composable
fun TacticalBadge(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
  filled: Boolean = false,
  dotColor: Color? = null,
) {
  val bg = if (filled) color.copy(alpha = 0.18f) else Color.Transparent
  val border = color.copy(alpha = 0.65f)
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(4.dp))
        .background(bg)
        .border(1.dp, border, RoundedCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (dotColor != null) {
      Box(
        modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor),
      )
      Spacer(modifier = Modifier.width(6.dp))
    }
    Text(
      text = text,
      color = color,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      fontSize = 11.sp,
      letterSpacing = 0.6.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun SectionHeader(
  title: String,
  trailing: String? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (trailing != null) {
      Text(
        text = trailing,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = TextMuted,
      )
    }
  }
}
