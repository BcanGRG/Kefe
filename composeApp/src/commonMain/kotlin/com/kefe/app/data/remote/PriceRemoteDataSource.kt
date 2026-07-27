package com.kefe.app.data.remote

import com.kefe.app.data.sample.SampleData
import com.kefe.app.domain.model.Price

/**
 * Uzak fiyat kaynagi.
 *
 * Kaynak secimi (TCMB gunluk kur, ucretsiz altin/doviz API'leri, TEFAS fon
 * kapanislari) henuz yapilmadi - hepsi farkli tazelik ve kota davraniyor.
 * Arayuz simdiden sabit tutuldu ki kaynak degistiginde ekranlar degismesin.
 *
 * Ktor istemcisi [createKefeHttpClient] ile kuruldu ve DI'da hazir; gercek
 * uygulama kaynak secildiginde bu arayuzun ardina girer.
 */
interface PriceRemoteDataSource {
    suspend fun fetchPrices(): List<Price>
}

/**
 * Kaynak secilene kadar kullanilan ornek veri saglayicisi.
 *
 * Uydurma bir ag cagrisi taklidi YAPMAZ - tasarim teslimindeki fiyat tablosunu
 * oldugu gibi dondurur, boylece ekranlar gercek rakamlarla calisir ve gecis
 * aninda yalniz bu sinif degisir.
 */
class SamplePriceRemoteDataSource : PriceRemoteDataSource {
    override suspend fun fetchPrices(): List<Price> = SampleData.prices
}
