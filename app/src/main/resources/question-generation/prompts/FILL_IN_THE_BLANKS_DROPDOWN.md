# Fill in the Blanks – Dropdown

Create a cloze test with 4-6 blanks. Each blank has a correct answer and 3 distractors in a dropdown.

## Requirements
- Passage: 150-250 words
- 4-6 blanks (indicated as __BLANK__)
- Each blank has 1 correct answer and 3 distractors
- Distractors must be wrong (no duplicates with answer)
- Different parts of speech recommended for variety

## JSON Example
```json
{
  "topic": "History and Culture",
  "passageWithSlots": "The ancient __BLANK__ civilization developed one of the world's earliest writing systems. Archaeological discoveries have revealed __BLANK__ structures and intricate artwork. Scholars believe this society __BLANK__ advanced knowledge in mathematics and astronomy. Trade routes __BLANK__ the empire to distant regions. This period __BLANK__ significant advances in technology and governance.",
  "slots": [
    {
      "answer": "Egyptian",
      "distractors": ["Roman", "Greek", "Chinese"]
    },
    {
      "answer": "magnificent",
      "distractors": ["ancient", "small", "mysterious"]
    },
    {
      "answer": "possessed",
      "distractors": ["developed", "knew", "discovered"]
    },
    {
      "answer": "connected",
      "distractors": ["linked", "established", "formed"]
    },
    {
      "answer": "witnessed",
      "distractors": ["recorded", "showed", "demonstrated"]
    }
  ]
}
```

## Instructions
Return ONLY the JSON object, no additional text.
