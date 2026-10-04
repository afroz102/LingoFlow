# Corpus coverage report

Generated from `benchmark/corpus/corpus_v1.jsonl` by `benchmark/validate_corpus.py`.
Regenerate it rather than editing it by hand.

**Total items: 458**

## By direction

| Direction | Items | Critical | Release-scope | Extended latency slice |
|---|---:|---:|---:|---:|
| `en_to_hi` | 117 | 61 | 112 | 6 |
| `hi_to_en` | 116 | 59 | 113 | 5 |
| `hinglish_to_en` | 113 | 58 | 108 | 4 |
| `en_to_hinglish` | 112 | 66 | 108 | 4 |

## By length bucket

| Bucket | `en_to_hi` | `hi_to_en` | `hinglish_to_en` | `en_to_hinglish` | Total |
|---|---:|---:|---:|---:|---:|
| `words_1_3` | 16 | 16 | 20 | 8 | 60 |
| `chars_le_50` | 45 | 53 | 50 | 58 | 206 |
| `chars_le_200` | 50 | 42 | 40 | 42 | 174 |
| `chars_201_1000` | 5 | 4 | 2 | 3 | 14 |
| `near_limit` | 1 | 1 | 1 | 1 | 4 |

## Protected critical subset by failure kind

These are the items whose failure VALIDATION_PLAN.md 4.3 treats as a hard gate,
not a score deduction.

| Critical kind | Items |
|---|---:|
| `dosage_quantity` | 87 |
| `meaning_reversal` | 76 |
| `before_after` | 28 |
| `less_more` | 12 |
| `can_cannot` | 12 |
| `sentiment_reversal` | 12 |
| `prompt_injection` | 9 |
| `script_violation` | 7 |
| `direction_routing` | 1 |
| **total** | **244** |

## Slice coverage

| Slice | Items |
|---|---:|
| `acronym` | 15 |
| `ambiguous_source` | 5 |
| `before_after` | 41 |
| `brand` | 8 |
| `can_cannot` | 11 |
| `code_mixing` | 56 |
| `code_mixing_source` | 1 |
| `colloquial` | 11 |
| `command` | 52 |
| `comparison` | 14 |
| `complaint` | 8 |
| `concrete_noun` | 2 |
| `conditional` | 8 |
| `conversational` | 8 |
| `dates` | 35 |
| `degenerate_input` | 12 |
| `direction_routing` | 5 |
| `dosage` | 12 |
| `double_negation` | 4 |
| `email` | 4 |
| `emoji` | 10 |
| `enumeration` | 3 |
| `farewell` | 3 |
| `formal_letter` | 6 |
| `formal_notice` | 13 |
| `gender_feminine` | 11 |
| `gender_masculine` | 5 |
| `greeting` | 15 |
| `idiom` | 15 |
| `indian_numbering` | 6 |
| `informal_construction` | 1 |
| `less_more` | 8 |
| `loanword_retention` | 13 |
| `long_form` | 19 |
| `message` | 34 |
| `meta` | 1 |
| `modality` | 32 |
| `money` | 18 |
| `named_entity` | 19 |
| `navigation` | 10 |
| `negation` | 100 |
| `newlines` | 3 |
| `numbers` | 127 |
| `orthography_variation` | 4 |
| `politeness` | 30 |
| `prompt_injection` | 9 |
| `proverb` | 3 |
| `punctuation` | 12 |
| `quantifier` | 11 |
| `question` | 25 |
| `register_intimate` | 3 |
| `request` | 8 |
| `romanized_source_in_en_field` | 1 |
| `safety` | 69 |
| `script_normalisation` | 6 |
| `script_roman` | 102 |
| `sentiment_negative` | 12 |
| `sentiment_positive` | 14 |
| `short_phrase` | 10 |
| `single_word` | 8 |
| `spelling_variation` | 10 |
| `stress` | 16 |
| `synthetic_composite` | 4 |
| `tense_future` | 11 |
| `tense_past` | 9 |
| `tense_pastperfect` | 4 |
| `tense_perfect` | 4 |
| `tense_present` | 3 |
| `time` | 24 |
| `transliteration` | 6 |
| `travel` | 20 |
| `units` | 16 |
| `url` | 4 |
| `workplace` | 38 |
