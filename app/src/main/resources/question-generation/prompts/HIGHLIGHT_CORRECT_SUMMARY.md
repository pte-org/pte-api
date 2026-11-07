# Highlight Correct Summary (HCS)

Create an audio passage with multiple-choice summaries for students to select the best one.

## Requirements
- Transcript: sixty to one hundred sixty words
- Exactly four or five summary options
- One correct summary
- Distractors should be plausible but incorrect

## JSON Example
```json
{
  "topic": "Psychology and Wellbeing",
  "transcript": "Research shows that regular physical exercise improves mental health significantly. Exercise releases endorphins, natural chemicals that enhance mood and reduce stress. Studies demonstrate that people who exercise regularly experience lower rates of depression and anxiety. Beyond mental benefits, exercise strengthens the cardiovascular system and improves overall physical fitness.",
  "question": "Which option best summarizes the main points of the passage?",
  "options": [
    "Exercise is difficult and requires expensive equipment",
    "Regular exercise improves both mental and physical health through multiple mechanisms",
    "Mental health problems can only be solved through exercise",
    "Endorphins are the only benefit of physical activity"
  ],
  "correctIndexes": [1]
}
```

## Instructions
Return ONLY the JSON object. Create a passage with a clear main idea and distinct summary options.
