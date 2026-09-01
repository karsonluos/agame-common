package com.robining.games.gpay

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import com.robining.games.frame.utils.App
import com.robining.games.frame.utils.AppLifeCycleListener
import com.robining.games.frame.utils.Net
import com.robining.games.ipay.IPay
import com.robining.games.ipay.IPay.Companion.TAG
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object GPay : IPay, PurchasesUpdatedListener, BillingClientStateListener {
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun IPay.IProduct.gpProductType(): String {
        return if (productType == IPay.IProductType.SUBSCRIPTION) {
            BillingClient.ProductType.SUBS
        } else {
            BillingClient.ProductType.INAPP
        }
    }

    private var mPendingCallback: PendingCallback? = null

    private fun success(): BillingResult {
        return BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()
    }

    private fun failed(code: Int, message: String): BillingResult {
        return BillingResult.newBuilder().setResponseCode(code).setDebugMessage(message)
            .build()
    }

    private val client: BillingClient = BillingClient.newBuilder(StartupContext.context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enablePrepaidPlans().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val empowerLock = Mutex()
    private lateinit var configuration: IPay.Configuration

    private fun connect() {
        if (client.connectionState == BillingClient.ConnectionState.DISCONNECTED) {
            Log.d(TAG, "start connect billing client")
            client.startConnection(this)
        }
    }

    private val ownProductsMutex = Mutex()
    val ownProductIds = mutableSetOf<String>()
    override val ownProductIdsLiveData: MutableLiveData<Set<String>> = MutableLiveData()

    override fun init(configuration: IPay.Configuration) {
        this.configuration = configuration
        connect()
        App.registerListener(object : AppLifeCycleListener {
            override fun onEnterApp() {
                connect()
            }
        })

        Net.doAfterConnectedAlways({
            connect()
        })
    }

    override suspend fun startPay(
        activity: FragmentActivity,
        product: IPay.IProduct,
        token: Any
    ): Result<Unit> {
        return startPay(activity, product, token, null)
    }

    override suspend fun startPay(
        activity: FragmentActivity,
        product: IPay.IProduct,
        token: Any,
        lifecycle: Lifecycle,
        callback: IPay.IPayCallback
    ): Result<Unit> {
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    if (mPendingCallback?.callback == callback) {
                        mPendingCallback = null
                    }
                }
            }
        })
        return startPay(activity, product, token, callback)
    }

    private suspend fun startPay(
        activity: FragmentActivity,
        product: IPay.IProduct,
        token: Any,
        callback: IPay.IPayCallback?
    ): Result<Unit> {
        connect()
        withContext(Dispatchers.Main){
            callback?.onPreChecking()
        }
        val params = token.toBillingProductDetailsParams()

        val billingResult = withContext(Dispatchers.Main) {
            callback?.onPaying()
            client.launchBillingFlow(
                activity, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(params)
                    )
                    .build()
            )
        }

        return if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "start pay success")
            mPendingCallback = callback?.let { PendingCallback(product, it) }
            Result.success(Unit)
        } else {
            Log.e(
                TAG,
                "start pay failed:${billingResult.responseCode},${billingResult.debugMessage}"
            )
            withContext(Dispatchers.Main){
                callback?.onPayFailed(billingResult.debugMessage)
            }
            mPendingCallback = null
            Result.failure(GPayException(billingResult))
        }
    }

    override suspend fun queryDetails(products: Array<IPay.IProduct>): Result<List<IPay.IProductDetail?>> {
        connect()
        Log.i(TAG, "query product details: ${products.joinToString { "${it.productId}(${it.gpProductType()})" }}")
        val result = queryProductDetail(*products)
        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val allProductDetails = result.productDetailsList.orEmpty()
            Log.i(TAG, "query product details success: returned=${allProductDetails.size}, requested=${products.size}")
            val sortedProductDetails = mutableListOf<ProductDetails?>()
            for (product in products) {
                val productDetail = allProductDetails.firstOrNull {
                    it.productId == product.productId && it.productType == product.gpProductType()
                }
                if (productDetail == null) {
                    Log.w(TAG, "product not returned by Google Play: id=${product.productId}, type=${product.gpProductType()}")
                }
                sortedProductDetails.add(productDetail)
            }

            val details = sortedProductDetails.mapIndexed { index, productDetail ->
                if (productDetail == null) {
                    null
                } else if (productDetail.productType == BillingClient.ProductType.SUBS) {
                    val offers = productDetail.subscriptionOfferDetails.orEmpty().map { offerDetail ->
                        val token = SubsPayToken(productDetail, offerDetail.offerToken)
                        IPay.PayOffer(
                            offerId = offerDetail.offerId,
                            basePlanId = offerDetail.basePlanId,
                            tags = offerDetail.offerTags.toSet(),
                            kind = if (offerDetail.offerId == null) {
                                IPay.OfferKind.REGULAR
                            } else {
                                IPay.OfferKind.SUBSCRIPTION_OFFER
                            },
                            pricePhases = offerDetail.pricingPhases.pricingPhaseList.map {
                                it.toPayPricePhase(token)
                            },
                            token = token,
                        )
                    }
                    Log.i(
                        TAG,
                        "subscription details: id=${productDetail.productId}, offers=${offers.size}, " +
                            offers.joinToString(prefix = "[", postfix = "]") { offer ->
                                "basePlan=${offer.basePlanId}, offerId=${offer.offerId ?: "regular"}, " +
                                    "tags=${offer.tags}, phases=${offer.pricePhases.joinToString { it.priceWithUnit }}"
                            }
                    )
                    IPay.SubProductDetail(
                        products[index],
                        pricePhases = offers.firstOrNull()?.pricePhases.orEmpty(),
                        content = productDetail.description,
                        offers = offers,
                    )
                } else {
                    val offers = productDetail.oneTimePurchaseOfferDetailsList
                        ?: productDetail.oneTimePurchaseOfferDetails?.let { listOf(it) }
                        ?: emptyList()
                    val payOffers = offers.mapNotNull { offerDetail ->
                        val offerToken = offerDetail.offerToken ?: return@mapNotNull null
                        val token = OneTimePayToken(productDetail, offerToken)
                        val discount = offerDetail.discountDisplayInfo
                        IPay.PayOffer(
                            offerId = offerDetail.offerId,
                            purchaseOptionId = offerDetail.purchaseOptionId,
                            tags = offerDetail.offerTags?.toSet().orEmpty(),
                            kind = when {
                                discount != null -> IPay.OfferKind.ONE_TIME_DISCOUNT
                                offerDetail.preorderDetails != null -> IPay.OfferKind.PREORDER
                                offerDetail.rentalDetails != null -> IPay.OfferKind.RENTAL
                                else -> IPay.OfferKind.REGULAR
                            },
                            pricePhases = listOf(
                                IPay.PricePhase(
                                    priceWithUnit = offerDetail.formattedPrice,
                                    phase = 1,
                                    phaseUnit = IPay.PricePhaseUnit.ONE_TIME,
                                    token = token,
                                    priceAmountMicros = offerDetail.priceAmountMicros,
                                    priceCurrencyCode = offerDetail.priceCurrencyCode,
                                ),
                            ),
                            discount = discount?.let {
                                IPay.Discount(
                                    amountWithUnit = it.discountAmount?.formattedDiscountAmount,
                                    percentage = it.percentageDiscount,
                                )
                            },
                            token = token,
                        )
                    }
                    val defaultOffer = payOffers.firstOrNull()
                    Log.i(
                        TAG,
                        "one-time details: id=${productDetail.productId}, offers=${payOffers.size}, " +
                            payOffers.joinToString(prefix = "[", postfix = "]") { offer ->
                                "option=${offer.purchaseOptionId}, offerId=${offer.offerId}, kind=${offer.kind}, " +
                                    "price=${offer.pricePhases.firstOrNull()?.priceWithUnit}, discount=${offer.discount}"
                            }
                    )
                    IPay.ProductDetail(
                        products[index],
                        priceWithUnit = defaultOffer?.pricePhases?.firstOrNull()?.priceWithUnit.orEmpty(),
                        content = productDetail.description,
                        token = defaultOffer?.token ?: productDetail,
                        offers = payOffers,
                    )
                }
            }

            Result.success(details)
        } else {
            Log.e(
                TAG,
                "query detail failed:${result.billingResult.responseCode},${result.billingResult.debugMessage}"
            )
            Result.failure(GPayException(result.billingResult))
        }
    }

    override suspend fun queryDetail(product: IPay.IProduct): Result<IPay.IProductDetail?> {
        val result = queryDetails(arrayOf(product))
        return if (result.isFailure) {
            Result.failure(result.exceptionOrNull()!!)
        } else {
            Result.success(result.getOrThrow().firstOrNull())
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.e(TAG, "billing service disconnected")
    }

    override fun onBillingSetupFinished(p0: BillingResult) {
        if (p0.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "billing service connect success")
            //每次连接恢复都尝试恢复购买
            scope.launch(Dispatchers.IO) {
                restoreAllPurchases()
            }
        } else {
            Log.e(TAG, "billing service connect failed:${p0.responseCode},${p0.debugMessage}")
        }
    }

    override suspend fun restoreAllPurchases(): Result<Unit> {
        connect()
        val billingResult = withContext(Dispatchers.IO) {
            val purchasesResult = queryPurchasesInner()
            if (purchasesResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return@withContext purchasesResult.billingResult
            }
            val purchases = purchasesResult.purchasesList
            var result =
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK)
                    .build()
            val tempOwnProductIds = mutableSetOf<String>()
            purchases.forEach { purchase ->
                val firstProductId = purchase.products.first()
                val firstProduct = configuration.findProductById(firstProductId)
                firstProduct?.let { product ->
                    //已拥有
                    if (product.productType != IPay.IProductType.IN_APP_CONSUME && purchase.purchaseState == Purchase.PurchaseState.PURCHASED && purchase.isAcknowledged) {
                        tempOwnProductIds.addAll(purchase.products)
                    }

                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED){
                        if (!purchase.isAcknowledged) {
                            val ackResult =
                                if (product.productType != IPay.IProductType.IN_APP_CONSUME) {
                                    ackInner(purchase)
                                } else {
                                    consumeInner(purchase)
                                }
                            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                                result = ackResult
                            }else{
                                //回执成功 重新添加
                                if (product.productType != IPay.IProductType.IN_APP_CONSUME) {
                                    tempOwnProductIds.addAll(purchase.products)
                                }
                            }
                        } else if (product.productType == IPay.IProductType.IN_APP_CONSUME && !configuration.isConsumed(purchase.purchaseToken)) {
                            if (!tryEmpower(purchase.purchaseToken, purchase.products)) {
                                result = BillingResult.newBuilder()
                                    .setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
                                    .build()
                            }
                        }
                    }
                }
            }

            //剔除已失效的项
            ownProductsMutex.withLock {
                ownProductIds.clear()
                ownProductIds.addAll(tempOwnProductIds)
                configuration.onRestoredPurchases(tempOwnProductIds.toList())
                ownProductIdsLiveData.postValue(ownProductIds)
            }

            val unEmpowerTokens = configuration.allUnEmpowerTokens()
            if (unEmpowerTokens.isNotEmpty()) {
                val historyResult = client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP).build()
                )
                if (historyResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    historyResult.purchasesList.forEach {
                        if (unEmpowerTokens.contains(it.purchaseToken)) {
                            tryEmpower(it.purchaseToken, it.products)
                        }
                    }
                }
            }

            return@withContext result
        }

        return if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "restore purchase success")
            Result.success(Unit)
        } else {
            Log.e(
                TAG,
                "restore purchase failed:${billingResult.responseCode},${billingResult.debugMessage}"
            )
            Result.failure(GPayException(billingResult))
        }
    }

    private suspend fun consumeOrAck(
        purchase: Purchase,
        callback: IPay.IPayCallback?
    ): BillingResult {
        val firstProductId = purchase.products.first()
        val firstProduct =
            configuration.findProductById(firstProductId) ?: return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE).build()
        val result = if (firstProduct.productType != IPay.IProductType.IN_APP_CONSUME) {
            ackInner(purchase)
        } else {
            consumeInner(purchase)
        }

        withContext(Dispatchers.Main){
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                callback?.onConfirmed()
            } else {
                callback?.onConfirmFailed(result.debugMessage)
            }
        }
        return result
    }

    private suspend fun consumeInner(purchase: Purchase): BillingResult {
        configuration.markConsumed(purchase.purchaseToken, false)
        val consumeResult = client.consumePurchase(
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        )
        return if (consumeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            if (tryEmpower(purchase.purchaseToken, purchase.products)) {
                consumeResult.billingResult
            } else {
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR).build()
            }
        } else {
            consumeResult.billingResult
        }
    }

    private suspend fun ackInner(purchase: Purchase): BillingResult {
        configuration.markConsumed(purchase.purchaseToken, false)
        val ackResult = client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        )

        return if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
            ownProductsMutex.withLock {
                ownProductIds.addAll(purchase.products)
                ownProductIdsLiveData.postValue(ownProductIds)
            }
            if (tryEmpower(purchase.purchaseToken, purchase.products)) {
                ackResult
            } else {
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR).build()
            }
        } else {
            ackResult
        }
    }

    private suspend fun tryEmpower(purchaseToken: String, productIds: List<String>): Boolean {
        empowerLock.withLock {
            if (configuration.isConsumed(purchaseToken)) {
                //该token已经处理过了 避免重复处理
                return true
            }

            if (configuration.empower(productIds)) {
                configuration.markConsumed(purchaseToken, true)
                return true
            }
            return false
        }
    }

    private suspend fun queryPurchasesInner(): PurchasesResult {
        val allPurchases = mutableListOf<Purchase>()
        var result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            allPurchases.addAll(result.purchasesList)
        } else {
            return PurchasesResult(result.billingResult, emptyList())
        }

        result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            allPurchases.addAll(result.purchasesList)
        } else {
            return PurchasesResult(result.billingResult, emptyList())
        }

        return PurchasesResult(
            BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK)
                .build(), allPurchases
        )
    }

    suspend fun queryProductDetail(vararg products: IPay.IProduct): ProductDetailsResult {
        return withContext(Dispatchers.IO) {
            queryProductDetailInner(*products)
        }
    }

    private suspend fun queryProductDetailInner(
        vararg products: IPay.IProduct
    ): ProductDetailsResult {
        //不能同时查询不同类型的，所以将不同类型的进行独立请求
        val allProductDetails = mutableListOf<ProductDetails>()
        val subsProductParams = products.filter {
            it.productType == IPay.IProductType.SUBSCRIPTION
        }.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId)
                .setProductType(BillingClient.ProductType.SUBS).build()
        }

        if (subsProductParams.isNotEmpty()) {
            val subsResult = client.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(subsProductParams).build()
            )
            if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                allProductDetails.addAll(subsResult.productDetailsList ?: emptyList())
            } else {
                return ProductDetailsResult(subsResult.billingResult, emptyList())
            }
        }

        val inAppProductParams = products.filter {
            it.productType != IPay.IProductType.SUBSCRIPTION
        }.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId)
                .setProductType(BillingClient.ProductType.INAPP).build()
        }

        if (inAppProductParams.isNotEmpty()) {
            val inAppResult = client.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(inAppProductParams)
                    .build()
            )
            if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                allProductDetails.addAll(inAppResult.productDetailsList ?: emptyList())
            } else {
                return ProductDetailsResult(inAppResult.billingResult, emptyList())
            }
        }

        return ProductDetailsResult(
            BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK)
                .build(), allProductDetails
        )
    }

    private fun Any.toBillingProductDetailsParams(): BillingFlowParams.ProductDetailsParams =
        when (this) {
            is SubsPayToken -> BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

            is OneTimePayToken -> BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

            is ProductDetails -> BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(this)
                .build()

            else -> throw IllegalArgumentException("Unsupported Google Play purchase token: ${this::class.java.name}")
        }

    private fun ProductDetails.PricingPhase.toPayPricePhase(token: Any): IPay.PricePhase {
        val billingPeriod = billingPeriod
        val match = billingPeriodPattern.matchEntire(billingPeriod)
            ?: throw IllegalArgumentException("Unsupported billingPeriod: $billingPeriod")
        val count = match.groupValues[1].toInt()
        val unit = when (match.groupValues[2]) {
            "D" -> IPay.PricePhaseUnit.DAY
            "W" -> IPay.PricePhaseUnit.WEEK
            "M" -> IPay.PricePhaseUnit.MONTH
            "Y" -> IPay.PricePhaseUnit.YEAR
            else -> throw IllegalArgumentException("Unsupported billingPeriod unit: $billingPeriod")
        }
        return IPay.PricePhase(
            priceWithUnit = formattedPrice,
            phase = count,
            phaseUnit = unit,
            token = token,
            priceAmountMicros = priceAmountMicros,
            priceCurrencyCode = priceCurrencyCode,
        )
    }

    private val billingPeriodPattern = Regex("P(\\d+)([DWMY])")

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        scope.launch(Dispatchers.Main){
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "pay failed:${billingResult.responseCode},${billingResult.debugMessage}")

                //匹配是否是当前交易的回调，可能是pending状态更新的回调
                val callback = mPendingCallback?.let { pendingCallback ->
                    if (purchases.isNullOrEmpty() || purchases.find {
                            it.products.contains(
                                pendingCallback.product.productId
                            )
                        } != null) {
                        mPendingCallback = null
                        pendingCallback.callback
                    } else {
                        null
                    }
                }
                if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    callback?.onPayCancel()
                } else {
                    callback?.onPayFailed(billingResult.debugMessage)
                }
                return@launch
            }

            purchases?.forEach {
                //匹配是否是当前交易的回调，可能是pending状态更新的回调
                val callback = mPendingCallback?.let { pendingCallback ->
                    if (it.products.contains(pendingCallback.product.productId)) {
                        mPendingCallback = null
                        pendingCallback.callback
                    } else {
                        null
                    }
                }
                if (it.purchaseState == Purchase.PurchaseState.PENDING) {
                    callback?.onPending()
                } else if (it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged) {
                    callback?.onPaySuccess()
                    scope.launch { consumeOrAck(it, callback) }
                }
            }
        }
    }

    private data class PendingCallback(val product: IPay.IProduct, val callback: IPay.IPayCallback)
}

data class SubsPayToken(val productDetails: ProductDetails, val offerToken: String)
data class OneTimePayToken(val productDetails: ProductDetails, val offerToken: String)
class GPayException(val billingResult: BillingResult) : Exception(billingResult.debugMessage)
