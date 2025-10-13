package com.robining.games.ipay

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData

interface IPay {
    companion object {
        const val TAG = "Pay"
    }

    val ownProductIdsLiveData: MutableLiveData<Set<String>>

    fun isOwn(product: IProduct): Boolean {
        return ownProductIdsLiveData.value?.contains(product.productId) ?: false
    }

    fun init(configuration: Configuration)

    suspend fun startPay(
        activity: FragmentActivity,
        product: IProduct,
        token: Any,
        lifecycle: Lifecycle,
        callback: IPayCallback
    ): Result<Unit>

    suspend fun startPay(
        activity: FragmentActivity,
        product: IProduct,
        token: Any
    ): Result<Unit>

    /**
     * 如果结果为null 表示待上线或已下架
     */
    suspend fun queryDetail(product: IProduct): Result<IProductDetail?>

    /**
     * 如果结果为null 表示待上线或已下架
     */
    suspend fun queryDetails(products: Array<IProduct>): Result<List<IProductDetail?>>

    suspend fun restoreAllPurchases(): Result<Unit>

    interface IPayCallback {
        /**
         * 检测环境中
         */
        suspend fun onPreChecking() {}

        /**
         * 支付中
         */
        suspend fun onPaying() {}

        /**
         * 等待支付结果中
         */
        suspend fun onPending() {}

        /**
         * 支付成功
         */
        suspend fun onPaySuccess() {}

        /**
         * 支付取消
         */
        suspend fun onPayCancel() {}

        /**
         * 支付失败
         */
        suspend fun onPayFailed(message: String) {}

        /**
         * 已确认消费
         */
        suspend fun onConfirmed() {}

        /**
         * 确认失败
         */
        suspend fun onConfirmFailed(message: String) {}
    }

    interface IProduct {
        val productId: String
        val productType: IProductType
    }

    sealed interface IProductDetail{
        val product: IProduct
    }

    data class ProductDetail(
        override val product: IProduct,
        val priceWithUnit: String,
        val content: String,
        val token: Any
    ) : IProductDetail

    data class SubProductDetail(
        override val product: IProduct,
        val pricePhases: List<PricePhase>,
        val content: String
    ) : IProductDetail

    data class PricePhase(
        val priceWithUnit: String,
        val phase: Int,
        val phaseUnit: PricePhaseUnit,
        val token: Any
    )

    enum class PricePhaseUnit {
        SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR
    }

    enum class IProductType {
        SUBSCRIPTION,
        IN_APP,
        IN_APP_CONSUME
    }

//    class Configuration(
//        val findProductById: (productId: String) -> IProduct?,
//        val markConsumed: (token: String, isConsumed: Boolean) -> Unit,
//        val empower: (productIds: List<String>) -> Boolean,
//        val isConsumed: (token: String) -> Boolean,
//        val allUnEmpowerTokens: () -> List<String>
//    )

    interface Configuration {
        fun findProductById(productId: String): IProduct?
        fun markConsumed(token: String, isConsumed: Boolean)
        suspend fun empower(productIds: List<String>): Boolean
        fun isConsumed(token: String): Boolean
        fun allUnEmpowerTokens(): List<String>
    }
}
