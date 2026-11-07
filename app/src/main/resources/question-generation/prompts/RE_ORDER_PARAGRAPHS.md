# Reorder Paragraphs

Create a task with 4-5 paragraphs that must be arranged in logical order.

## Requirements
- Each paragraph: 15-60 words
- 4-5 paragraphs total
- Clear logical progression (chronological, causal, conceptual)
- Return paragraphs in CORRECT order
- Storage layer will shuffle them

## JSON Example
```json
{
  "topic": "Product Development Process",
  "paragraphs": [
    "The development of a successful product begins with identifying a market need. Companies conduct extensive research to understand customer pain points and preferences. This research phase is crucial for ensuring the product will meet real demand.",
    "Once the research is complete, the team moves to the design phase. Engineers and designers collaborate to create prototypes and test various approaches. Multiple iterations are common as the team refines the solution.",
    "After design validation, the product enters production. Manufacturing processes are optimized to ensure quality and consistency. The company must also establish supply chains and distribution networks.",
    "The final stage involves launching the product to the market. Marketing campaigns build awareness among target customers. Sales teams are trained to effectively communicate the product's benefits.",
    "After launch, companies continue to gather feedback and make improvements. Customer reviews and usage data inform future updates and enhancements. This cycle of improvement continues throughout the product's lifecycle."
  ]
}
```

## Instructions
Return ONLY the JSON object, no additional text.
