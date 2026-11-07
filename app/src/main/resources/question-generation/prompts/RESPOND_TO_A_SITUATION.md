# Respond to a Situation (RTS) Generation Prompt

You are generating a Respond to a Situation task for the PTE exam. The student listens to a brief scenario and 1–2 turns of dialogue, then responds to complete the conversation.

## Task Structure
The student hears a situation (40–90 words) and optionally 1–2 dialogue turns. The student must respond appropriately (typically with a question or statement, 10–30 seconds of speaking time, roughly 50–100 words).

## Output JSON Schema
```json
{
  "taskType": "RESPOND_TO_A_SITUATION",
  "topic": "string: brief topic name (3-5 words, no special chars)",
  "situation": "string: the background scenario (40–90 words)",
  "turns": [
    {
      "speaker": "A" | "B",
      "text": "string: a prompt or statement the student must respond to"
    }
  ],
  "keyPoints": [
    "string: one thing the ideal response should address"
  ]
}
```

## Requirements
1. **Situation**: clear, realistic scenario (40–90 words) that sets context.
2. **1–2 dialogue turns**: one or two lines to which the student responds.
3. **Speakers**: typically "A" (person in the situation) and optionally "B" (if two turns).
4. **Numbers and abbreviations**: spell out fully.
5. **No acronyms**: always use full form.
6. **Natural context**: the scenario should naturally lead to a response.
7. **2–4 key points**: expectations for the student's response.

## Example Output
```json
{
  "taskType": "RESPOND_TO_A_SITUATION",
  "topic": "Job Interview Feedback",
  "situation": "You are at a job interview for a marketing position at a tech company. The interviewer has just finished asking you questions and now wants to hear your thoughts on the role and the company.",
  "turns": [
    {"speaker": "A", "text": "Thank you for all those questions. Do you have any questions for us or anything you would like to know about the company culture?"}
  ],
  "keyPoints": [
    "Student demonstrates knowledge of the company",
    "Student asks thoughtful questions about growth or role expectations",
    "Student shows enthusiasm and professionalism"
  ]
}
```

## Validation Rules (enforced after generation)
- Situation is 40–90 words
- 1–2 dialogue turns
- Speakers are "A" or "B"
- No numbers or acronyms in text
- Key points are achievable and clear
