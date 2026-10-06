"""Draws the three DESCRIBE_IMAGE demo charts (bar, line, pie) with Pillow."""
import argparse
import json
import math
import os

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1000, 650
BACKGROUND = (255, 255, 255)
INK = (33, 37, 41)
GRID = (222, 226, 230)
PALETTE = [(30, 136, 229), (67, 160, 71), (251, 140, 0), (142, 36, 170), (229, 57, 53), (0, 137, 123)]
FONT_PATHS = ["C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]


def load_font(size):
    for path in FONT_PATHS:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def draw_heading(draw, text):
    font = load_font(30)
    width = draw.textlength(text, font=font)
    draw.text(((WIDTH - width) / 2, 28), text, fill=INK, font=font)


def draw_axes(draw, labels, max_value):
    font = load_font(20)
    left, top, right, bottom = 110, 110, WIDTH - 60, HEIGHT - 90
    step = max(1, int(math.ceil(max_value / 5 / 10.0)) * 10)
    top_value = step * 5
    for tick in range(0, top_value + 1, step):
        y = bottom - (bottom - top) * tick / top_value
        draw.line([(left, y), (right, y)], fill=GRID, width=1)
        draw.text((left - 12 - draw.textlength(str(tick), font=font), y - 10), str(tick), fill=INK, font=font)
    draw.line([(left, top), (left, bottom)], fill=INK, width=2)
    draw.line([(left, bottom), (right, bottom)], fill=INK, width=2)
    slot = (right - left) / len(labels)
    for i, label in enumerate(labels):
        x = left + slot * (i + 0.5)
        draw.text((x - draw.textlength(label, font=font) / 2, bottom + 14), label, fill=INK, font=font)
    return left, top, right, bottom, top_value, slot


def render_bar(item):
    image = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw_heading(draw, item["heading"])
    left, top, right, bottom, top_value, slot = draw_axes(draw, item["labels"], max(item["values"]))
    font = load_font(20)
    for i, value in enumerate(item["values"]):
        x0 = left + slot * i + slot * 0.2
        x1 = left + slot * (i + 1) - slot * 0.2
        y = bottom - (bottom - top) * value / top_value
        draw.rectangle([x0, y, x1, bottom], fill=PALETTE[0])
        text = str(value)
        draw.text(((x0 + x1) / 2 - draw.textlength(text, font=font) / 2, y - 26), text, fill=INK, font=font)
    return image


def render_line(item):
    image = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw_heading(draw, item["heading"])
    left, top, right, bottom, top_value, slot = draw_axes(draw, item["labels"], max(item["values"]))
    font = load_font(20)
    points = []
    for i, value in enumerate(item["values"]):
        x = left + slot * (i + 0.5)
        y = bottom - (bottom - top) * value / top_value
        points.append((x, y))
    draw.line(points, fill=PALETTE[0], width=4)
    for (x, y), value in zip(points, item["values"]):
        draw.ellipse([x - 7, y - 7, x + 7, y + 7], fill=PALETTE[4])
        text = str(value)
        draw.text((x - draw.textlength(text, font=font) / 2, y - 32), text, fill=INK, font=font)
    return image


def render_pie(item):
    image = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw_heading(draw, item["heading"])
    font = load_font(22)
    box = [90, 110, 90 + 440, 110 + 440]
    total = float(sum(item["values"]))
    start = -90.0
    for i, value in enumerate(item["values"]):
        sweep = 360.0 * value / total
        draw.pieslice(box, start, start + sweep, fill=PALETTE[i % len(PALETTE)], outline=BACKGROUND, width=3)
        start += sweep
    legend_x, legend_y = 600, 190
    for i, (label, value) in enumerate(zip(item["labels"], item["values"])):
        y = legend_y + i * 52
        draw.rectangle([legend_x, y, legend_x + 30, y + 30], fill=PALETTE[i % len(PALETTE)])
        draw.text((legend_x + 46, y + 2), f"{label}: {value}%", fill=INK, font=font)
    return image


RENDERERS = {"bar": render_bar, "line": render_line, "pie": render_pie}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--content", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    with open(args.content, encoding="utf-8") as handle:
        content = json.load(handle)
    os.makedirs(args.out, exist_ok=True)
    for item in content["describeImage"]:
        RENDERERS[item["kind"]](item).save(os.path.join(args.out, f"demo22-{item['key']}.png"))


if __name__ == "__main__":
    main()
