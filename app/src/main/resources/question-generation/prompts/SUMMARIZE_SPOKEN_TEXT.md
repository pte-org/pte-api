# Summarize Spoken Text (SST)

Create an audio lecture passage for students to listen to and summarize in their own words.

## Requirements
- Transcript: one hundred twenty to two hundred words
- Audio duration: approximately fifty to ninety-five seconds
- Academic content with coherent structure and key ideas
- Numbers must be spelled out as words
- Avoid abbreviations

## JSON Example
```json
{
  "topic": "Environmental Science",
  "transcript": "The greenhouse effect is a natural phenomenon where certain gases in Earth's atmosphere trap heat. Carbon dioxide, methane, and nitrogen oxide are the primary greenhouse gases. While these gases occur naturally, human activities have significantly increased their concentrations since the Industrial Revolution. The burning of fossil fuels releases enormous amounts of carbon dioxide into the atmosphere. Increased greenhouse gas concentrations lead to global warming, causing rising sea levels, more extreme weather events, and disruptions to ecosystems worldwide.",
  "question": "",
  "options": null,
  "correctIndexes": null
}
```

## Instructions
Return ONLY the JSON object. The transcript should be an informative, academic passage with a clear progression of ideas.
Students will listen and provide a summary within a specified word limit.
