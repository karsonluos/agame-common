# Payment offers and discounts

`IPay.IProductDetail.offers` is the cross-channel source of selectable purchase
options. Every payment implementation should return at least one
`IPay.PayOffer` when a product can be purchased, including channels that do not
support promotions.

```kotlin
val detail = pay.queryDetail(product).getOrThrow() ?: return
val offer = detail.offers.firstOrNull() ?: return
pay.startPay(activity, product, offer)
```

For a product detail shown in an existing UI, `ProductDetail.priceWithUnit` and
`SubProductDetail.pricePhases` remain available as the default offer's display
data. New UI should render `offers` so the player can select a plan or discount.

## Provider mapping

- Google Play Billing: every eligible subscription base plan or subscription
  offer is returned separately. One-time purchase options and discounts are also
  returned separately. Each offer's opaque token is passed to the billing flow.
- Channels without native discount APIs: return one `OfferKind.REGULAR` offer
  with their normal checkout token.
- Channels with coupons, campaigns, introductory prices, rentals, or preorders:
  return one offer per eligible option, populate `offerId`, `tags`,
  `pricePhases`, and optional `discount` information, and keep the provider
  checkout payload in `PayOffer.token`.

`PayOffer.token` is intentionally opaque. The abstract library never assumes a
Google Play class, so providers such as Huawei IAP, Samsung Galaxy Store, Amazon
Appstore, or a server-hosted checkout can implement the same contract.
