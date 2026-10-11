package com.haodong.yimalaile.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object DisclaimerRoute
@Serializable data object OnboardingRoute
@Serializable data object PeopleRoute
@Serializable data class PersonDetailRoute(val personId: String)
@Serializable data class PersonEditRoute(val personId: String? = null)
@Serializable data object SettingsRoute
@Serializable data object NotificationSettingsRoute