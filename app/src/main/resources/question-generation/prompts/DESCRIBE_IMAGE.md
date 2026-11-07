# Describe Image (Chart) Generation Prompt

You are generating a Describe Image task with a chart (graph, bar chart, line chart, pie chart, or table) for the PTE exam. The student must describe the data visually shown in the chart.

## Task Structure
Generate numerical data that will be rendered into a chart, along with key points that describe important features of that data.

## Output JSON Schema
```json
{
  "taskType": "DESCRIBE_IMAGE",
  "topic": "string: brief title for the chart (5–10 words, no special chars)",
  "chartType": "BAR" | "LINE" | "PIE" | "TABLE",
  "title": "string: the chart title (same as topic or slightly expanded)",
  "xLabel": "string: (optional for PIE and TABLE) label for x-axis",
  "yLabel": "string: (optional for PIE and TABLE) label for y-axis",
  "unit": "string: e.g., 'millions of dollars', 'percentage', 'number of visitors'",
  "categories": [
    "string: category name for each bar/line/row"
  ],
  "series": [
    {
      "name": "string: name of the data series",
      "values": [number, number, ...]
    }
  ],
  "keyPoints": [
    {
      "kind": "MAX" | "MIN" | "TREND_UP" | "TREND_DOWN" | "COMPARE" | "TOTAL",
      "text": "string: description in student's language (English or Vietnamese)",
      "reference": "string: which series/category/value this refers to (for validation)"
    }
  ]
}
```

## Chart Type Rules
1. **BAR**: 3–8 categories, 1–3 series, all values ≥ 0.
2. **LINE**: 3–8 categories, 1–3 series, all values ≥ 0.
3. **PIE**: 3–8 slices, exactly 1 series, all values ≥ 0, **sum must equal 100 ± 0.5**.
4. **TABLE**: 3–6 rows, 2–5 columns, all values ≥ 0.

## Requirements
1. **Realistic data**: use plausible numbers for the topic.
2. **Mix of key point types**: include MAX, MIN, TREND, and/or COMPARE.
3. **2–4 key points** that highlight important insights.
4. **No acronyms** in title or labels.
5. **Numbers as digits** in the data array (not spelled out).
6. **Key point text** can be in English or Vietnamese; it is stored as the reference for grading.

## Example Output
```json
{
  "taskType": "DESCRIBE_IMAGE",
  "topic": "Renewable Energy Adoption",
  "chartType": "BAR",
  "title": "Renewable Energy Adoption by Region (2020-2024)",
  "xLabel": "Year",
  "yLabel": "Percentage",
  "unit": "percent",
  "categories": ["2020", "2021", "2022", "2023", "2024"],
  "series": [
    {"name": "Europe", "values": [35, 38, 42, 46, 50]},
    {"name": "Asia", "values": [20, 22, 25, 28, 32]},
    {"name": "Americas", "values": [25, 26, 28, 30, 33]}
  ],
  "keyPoints": [
    {"kind": "MAX", "text": "Europe had the highest renewable adoption in 2024 at fifty percent", "reference": "Europe 2024"},
    {"kind": "TREND_UP", "text": "All regions showed consistent growth from 2020 to 2024", "reference": "all series"},
    {"kind": "COMPARE", "text": "Europe leads both Asia and Americas by at least 18 percentage points", "reference": "Europe vs Asia"}
  ]
}
```

## Validation Rules (enforced after generation)
- Chart type is BAR, LINE, PIE, or TABLE
- Number of categories within limits (3–8 for BAR/LINE, 3–8 for PIE as slices, 3–6 rows for TABLE)
- All values ≥ 0
- PIE: sum of series = 100 ± 0.5
- Key point `kind` matches actual data (e.g., MAX must identify the actual maximum)
- 2–4 key points
