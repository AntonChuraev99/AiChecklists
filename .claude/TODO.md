# Claude Code TODO

## Completed

- [x] Text overflow fix in HorizontalPager (PaywallScreen, OnboardingScreen)
- [x] PurchasesDelegate for pending transactions
- [x] Support email button in MainScreen and PaywallScreen
- [x] Success snackbar after purchase
- [x] Navigation with showSuccessMessage param
- [x] CLAUDE.md UI best practices documentation

## Planned Improvements

### UX Analysis Agent
- [ ] Add an agent that waits for feature implementation to complete
- [ ] After feature is done, agent automatically analyzes UX
- [ ] Agent should:
  - Review new screens and components
  - Check for UX consistency with existing design system
  - Identify missing validation, feedback, or accessibility issues
  - Propose and implement UX improvements
  - Document findings in CLAUDE.md

### iOS Setup
- [ ] Configure iOS subscription in App Store Connect
- [ ] Add RevenueCat iOS API key
- [ ] Test sandbox purchases on iOS

### Future Features
- [ ] Referral program
- [ ] Checklist sharing between users

### Store Promotions

#### Annual Subscription Promo (30% margin guaranteed)

**Расчёт цены:**
| Параметр | Значение |
|----------|----------|
| Месячная цена | $1.99 |
| Годовая без скидки | $23.88 |
| Комиссия Google | 15% |
| Себестоимость AI (max) | $7.20/год |
| **Целевая маржа** | **≥30%** |

**Формула:** `(Цена × 0.85 - Себестоимость) / Цена ≥ 30%`

**Результат:**
| Параметр | Значение |
|----------|----------|
| **Годовая цена** | **$12.99** |
| Скидка | 45% |
| Эквивалент в месяц | $1.08/мес |
| Чистый доход | $11.04/год |
| Маржа (max usage) | 31% |
| Маржа (avg usage) | 72% |

**TODO:**
- [ ] Создать годовую подписку в Google Play Console
  - Product ID: `premium_annual`
  - Base plan: `annual`
  - Цена: $12.99/год
- [ ] Создать в App Store Connect
- [ ] Добавить в RevenueCat Offering
- [ ] Обновить PaywallScreen с выбором плана
- [ ] Маркетинг: "Save 45% with annual plan!"
