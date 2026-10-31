// Illustrations reconstructed from the source answers, not original exam images.
// Every plotted value is present in pte-doc's canonical Describe Image records.
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';

const svg = (title, body, footnote) => `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="620" viewBox="0 0 1000 620">
<rect width="1000" height="620" fill="#fff"/>
<g font-family="Arial, sans-serif" fill="#182637">
<text x="55" y="55" font-size="30" font-weight="bold">${title}</text>
${body}
<text x="55" y="575" font-size="16">${footnote}</text>
<text x="55" y="602" font-size="13" fill="#596777">Local test illustration reconstructed from source reference text.</text>
</g></svg>`;

export async function describeImageSupplements(canonical, apiRoot) {
  const require = createRequire(fs.realpathSync(path.join(apiRoot, '..', 'pte-web', 'apps', 'tenant-web', 'node_modules', 'next', 'package.json')));
  const sharp = require('sharp');
  const tree = svg('Heights of selected tree species', `
<text x="55" y="93" font-size="18">Approximate height (feet)</text>
${[['Hemlock', 130], ['Cedar', 200], ['Douglas fir', 280]].map(([label, value], index) => {
    const y = 150 + index * 115;
    return `<text x="55" y="${y + 33}" font-size="21">${label}</text>
<rect x="220" y="${y}" width="${value * 2.3}" height="55" rx="3" fill="#3379b5"/>
<text x="${235 + value * 2.3}" y="${y + 35}" font-size="22">${value}</text>`;
  }).join('')}`, 'Selected species and approximate figures from “Height of Tree”.');
  const power = svg('Electricity generation in China: 1994 and 2004', `
<text x="55" y="93" font-size="18">Source values (original units)</text>
${[['Conventional thermal', 600, 1500], ['Hydroelectric', 100, 100], ['Total generation', 900, 2000]].map(([label, first, last], index) => {
    const x = 95 + index * 310;
    return `<rect x="${x}" y="${475 - first * 0.16}" width="65" height="${first * 0.16}" fill="#3379b5"/>
<rect x="${x + 85}" y="${475 - last * 0.16}" width="65" height="${last * 0.16}" fill="#db8745"/>
<text x="${x + 30}" y="${463 - first * 0.16}" text-anchor="middle" font-size="18">${first}</text>
<text x="${x + 115}" y="${463 - last * 0.16}" text-anchor="middle" font-size="18">${last}</text>
<text x="${x + 75}" y="510" text-anchor="middle" font-size="18">${label}</text>`;
  }).join('')}
<rect x="330" y="118" width="18" height="18" fill="#3379b5"/><text x="360" y="133" font-size="18">1994</text>
<rect x="490" y="118" width="18" height="18" fill="#db8745"/><text x="520" y="133" font-size="18">2004</text>`,
  'Endpoints and approximate figures from “Electricity Generation”.');
  const result = [];
  for (const [title, chart] of [['Height of Tree', tree], ['Electricity Generation', power]]) {
    const original = canonical.find((record) => record.title === title);
    if (!original?.referenceAnswerText) throw Error(`Missing source record: ${title}`);
    const png = await sharp(Buffer.from(chart)).png().toBuffer();
    result.push({ ...original,
      imagePromptUrl: `data:image/png;base64,${png.toString('base64')}`,
      source: { ...original.source, kind: 'mock', illustration: 'reconstructed-from-reference-v1' },
      importWarnings: ['ILLUSTRATION_RECONSTRUCTED_FROM_REFERENCE'],
    });
  }
  return result;
}
