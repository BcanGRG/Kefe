package com.kefe.app.domain.model

/** Paylasilan portfoy. Uyeler yalniz kimlikle tutulur. */
data class Portfolio(
    val id: String,
    val name: String,
    val currency: String,
    val memberIds: List<String>,
)

/**
 * Profil.
 *
 * ROL ve IZIN KALDIRILDI: uygulama iki kisilik, tek hesap. "Sahip / uye" ve
 * "duzenleyebilir / sadece goruntuler" ayrimi cok kullanicili bir modelin
 * kalintisiydi - iki esit profilde karsiligi yok. lastSeen de gitti: gercek bir
 * zaman damgasi degil, hicbir yerde yazilmayan bir ekran metniydi.
 */
data class Member(
    val id: String,
    val name: String,
    val initials: String,
)
