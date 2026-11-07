# Summarize Written Text

Create a passage with key points for test-takers to summarize in one sentence.

## Requirements
- Passage: 200-300 words
- 3-4 key points
- Points should capture main ideas
- Passage should be cohesive

## JSON Example
```json
{
  "topic": "Renewable Energy",
  "passage": "The global energy landscape is undergoing significant transformation as countries shift toward renewable energy sources. Solar and wind power have become increasingly competitive with fossil fuels in terms of cost and efficiency. Many nations have implemented ambitious targets to reduce carbon emissions and increase renewable energy capacity. Germany, for example, generates over fifty percent of its electricity from renewable sources. However, challenges remain in energy storage and grid integration. Battery technology has improved dramatically in recent years, but further development is needed. The transition also requires substantial investment in infrastructure and workforce retraining. Despite these challenges, the momentum toward renewable energy continues to accelerate globally.",
  "keyPoints": [
    "Global energy shift toward renewable sources due to cost competitiveness",
    "Challenges in energy storage and grid integration remain",
    "Significant investment needed in infrastructure and workforce"
  ]
}
```

## Instructions
Return ONLY the JSON object, no additional text.
