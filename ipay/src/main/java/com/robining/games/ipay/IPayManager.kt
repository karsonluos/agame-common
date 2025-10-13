package com.robining.games.ipay

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData

object IPayManager : IPay {
    private var pay: IPay? = null

    fun register(pay: IPay) {
        this.pay = pay
    }

    override val ownProductIdsLiveData: MutableLiveData<Set<String>>
        get() = pay!!.ownProductIdsLiveData

    override fun init(configuration: IPay.Configuration) {
        pay!!.init(configuration)
    }

    override suspend fun startPay(
        activity: FragmentActivity,
        product: IPay.IProduct,
        token: Any,
        lifecycle: Lifecycle,
        callback: IPay.IPayCallback
    ): Result<Unit> {
        return pay!!.startPay(activity, product, token, lifecycle, callback)
    }

    override suspend fun startPay(
        activity: FragmentActivity,
        product: IPay.IProduct,
        token: Any
    ): Result<Unit> {
        return pay!!.startPay(activity, product, token)
    }

    override suspend fun queryDetail(product: IPay.IProduct): Result<IPay.IProductDetail?> {
        return pay!!.queryDetail(product)
    }

    override suspend fun queryDetails(products: Array<IPay.IProduct>): Result<List<IPay.IProductDetail?>> {
        return pay!!.queryDetails(products)
    }

    override suspend fun restoreAllPurchases(): Result<Unit> {
        return pay!!.restoreAllPurchases()
    }
}