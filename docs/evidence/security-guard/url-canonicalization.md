# Local URL canonicalization validation

Date: 22 September 2026

The deterministic URL analyzer was exercised through JVM tests. It performs no DNS lookup, reputation request or other network access.

## Covered cases

- Unicode domain labels are normalized with NFKC and converted through Java IDN/STD3 rules before structural checks.
- Ordinary accented Latin and Japanese internationalized domains remain low when no other structural signal is present.
- A label mixing Cyrillic and Latin characters to resemble `paypal` produces both mixed-script and brand-confusable evidence and reaches `HIGH_ATTENTION`.
- Hidden control/direction characters, backslashes, user-info destinations, trailing dots, raw IPv4/IPv6, numeric or hexadecimal hosts, invalid ports and non-HTTP(S) schemes do not score low.
- Percent-encoded executable suffixes are decoded for the local path check.
- Message extraction removes ordinary closing punctuation before passing a URL to the analyzer.
- Findings use the generic subject `Link`; submitted URLs are not copied into the subject.

## Validation

The full JVM suite passed with 124 tests: core 20, security-core 7, security-scanner 9 and app 88. Android debug lint also passed.

## Limits

This is structural analysis. It has no DNS, redirect, certificate, age, reputation or content evidence. The confusable map intentionally covers a small set of high-value Latin/Greek/Cyrillic look-alikes and brands; it is not a complete implementation of the Unicode confusables data. A low structural result does not establish that a destination is safe.
