# Localisation rules

- `values`, `values-tr` and `values-ro` contain the same keys, all non-empty, with the same
  placeholders (`%1$s`, `%2$d`). `LocalizationParityTest` checks this.
- Every new string is added to all three files in the same change.
- Data from the TV or a service (dates, app names, language names, city names) is shown as is.
- Escape apostrophes in Android strings (`\'`).
