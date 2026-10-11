package com.haodong.yimalaile.ui.pages.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.settings.SettingsRepository
import com.haodong.yimalaile.ui.pages.home.HomeScreen
import com.haodong.yimalaile.ui.pages.sheet.LocalSheetViewModel
import com.haodong.yimalaile.ui.pages.sheet.SheetHost
import com.haodong.yimalaile.ui.pages.sheet.SheetViewModel

/**
 * The full tracking experience for one person: wraps [HomeScreen] with a
 * person-scoped [SheetViewModel] (and its sheet host), so sheets operate on
 * exactly this person's cycle.
 */
@Composable
fun PersonDetailScreen(
    service: MenstrualService,
    settings: SettingsRepository,
    person: Person,
    onBack: () -> Unit,
    onEditPerson: () -> Unit,
) {
    val sheetViewModel = remember(person.id) { SheetViewModel(service, person.id) }

    CompositionLocalProvider(LocalSheetViewModel provides sheetViewModel) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                service = service,
                sheetViewModel = sheetViewModel,
                person = person,
                settings = settings,
                onNavigateSettings = onEditPerson,
                onNavigatePeople = onBack,
            )
            SheetHost(sheetViewModel)
        }
    }
}