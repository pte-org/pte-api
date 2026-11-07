# Summarize Group Discussion (SGD) Generation Prompt

You are generating a Summarize Group Discussion task for the PTE exam. This task has **3 speakers** (A, B, C) discussing a topic over 6–12 turns, with a total of 180–280 words.

## Task Structure
The student listens to a dialogue between three speakers and writes a 50-70 word summary of the discussion.

## Output JSON Schema
```json
{
  "taskType": "SUMMARIZE_GROUP_DISCUSSION",
  "topic": "string: brief topic name (3-5 words, no special chars)",
  "turns": [
    {
      "speaker": "A" | "B" | "C",
      "text": "string: what the speaker says (one sentence or short phrase)"
    }
  ],
  "keyPoints": [
    "string: one key point that the student's summary should cover"
  ]
}
```

## Requirements
1. **Three distinct speakers** (A, B, C), each with 2+ turns.
2. **6–12 turns total**: a natural back-and-forth discussion.
3. **Total of 180–280 words** across all turns.
4. **Numbers and abbreviations**: spell out fully (e.g., "2025" → "twenty twenty-five", "USA" → "United States of America").
5. **No acronyms or abbreviated terms**: always use full form.
6. **Natural flow**: speakers build on each other's points.
7. **3–5 key points** that summarize the main ideas.

## Example Output
```json
{
  "taskType": "SUMMARIZE_GROUP_DISCUSSION",
  "topic": "Renewable Energy",
  "turns": [
    {"speaker": "A", "text": "I think renewable energy is crucial for the environment. Solar and wind power are becoming increasingly affordable."},
    {"speaker": "B", "text": "Absolutely. My company recently invested in solar panels and reduced our electricity costs by thirty percent."},
    {"speaker": "C", "text": "That's impressive, but we need government support to make it mainstream. Infrastructure is a big challenge."},
    {"speaker": "A", "text": "Good point. Countries like Germany and Denmark have shown it is possible with proper planning."},
    {"speaker": "B", "text": "Education is also important. People need to understand the long-term benefits beyond just cost savings."}
  ],
  "keyPoints": [
    "Renewable energy reduces environmental impact and costs",
    "Solar technology is becoming more affordable for businesses",
    "Government support and infrastructure are necessary for scaling",
    "Education helps people understand the long-term benefits"
  ]
}
```

## Validation Rules (enforced after generation)
- Exactly 3 speakers (A, B, C)
- Each speaker has at least 2 turns
- 6–12 turns total
- 180–280 words total
- No numbers or acronyms in text
