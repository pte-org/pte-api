# Fill in the Blanks – Drag and Drop

Create a gap-fill task with 3-5 blanks and a word bank. Candidates drag words to fill gaps.

## Requirements
- Passage: 150-250 words
- 3-5 blanks (indicated as __BLANK__)
- Word bank has correct answers + 2-3 extra words
- No duplicate words in word bank
- Different word types recommended

## JSON Example
```json
{
  "topic": "Science and Innovation",
  "passageWithSlots": "The discovery of penicillin was __BLANK__ by accident when Alexander Fleming noticed mold on a bacterial culture. This __BLANK__ finding led to the development of the first antibiotic. The __BLANK__ of antibiotics has saved millions of lives worldwide. Before this __BLANK__, infections often resulted in serious complications. Today, these medicines remain __BLANK__ in medical treatment.",
  "slots": [
    {
      "answer": "made",
      "distractors": ["create", "done"]
    },
    {
      "answer": "remarkable",
      "distractors": ["unusual", "great"]
    },
    {
      "answer": "introduction",
      "distractors": ["development", "creation"]
    },
    {
      "answer": "breakthrough",
      "distractors": ["achievement", "milestone"]
    },
    {
      "answer": "essential",
      "distractors": ["vital", "important"]
    }
  ]
}
```

## Instructions
Return ONLY the JSON object, no additional text.
