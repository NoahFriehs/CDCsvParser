package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import at.msd.friehs_bicha.cdcsvparser.R

/**
 * About screen (Compose, P3.1). Mirrors the old `activity_about_us.xml`
 * content one-to-one; the mail row opens the email chooser (fragment side).
 */
@Composable
fun AboutScreen(onMailClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(stringResource(R.string.contributers))
        SubItem(stringResource(R.string.names))
        IndentedItem(stringResource(R.string.bicha_stefan_friehs_noah_dennig_rosa))
        SubItem(stringResource(R.string.git))
        IndentedItem(stringResource(R.string.git_bicha))
        IndentedItem(stringResource(R.string.git_noah))
        IndentedItem(stringResource(R.string.git_rosa))

        Spacer(Modifier.height(24.dp))
        Paragraph(stringResource(R.string.support_string))
        Paragraph(stringResource(R.string.btc_adress))
        Paragraph(stringResource(R.string.eth_adress))

        Spacer(Modifier.height(24.dp))
        Paragraph(stringResource(R.string.hope_useful_string))
        Paragraph(stringResource(R.string.problem_contact_string))

        Text(
            text = stringResource(R.string.cdcsvparser_gmail),
            modifier = Modifier.clickable(onClickLabel = "mail") { onMailClick() },
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun SubItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun IndentedItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp),
    )
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
    )
}
