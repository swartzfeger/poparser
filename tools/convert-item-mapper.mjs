import fs from "fs";
import path from "path";
import vm from "vm";

function extractObjectLiteral(source, constName) {
  const marker = `const ${constName}`;
  const start = source.indexOf(marker);
  if (start === -1) {
    throw new Error(`Could not find ${constName}`);
  }

  const firstBrace = source.indexOf("{", start);
  if (firstBrace === -1) {
    throw new Error(`Could not find opening brace for ${constName}`);
  }

  let depth = 0;
  let inString = false;
  let stringQuote = "";
  let escaped = false;

  for (let i = firstBrace; i < source.length; i++) {
    const ch = source[i];

    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (ch === "\\") {
        escaped = true;
      } else if (ch === stringQuote) {
        inString = false;
        stringQuote = "";
      }
      continue;
    }

    if (ch === `"` || ch === `'`) {
      inString = true;
      stringQuote = ch;
      continue;
    }

    if (ch === "{") depth++;
    if (ch === "}") {
      depth--;
      if (depth === 0) {
        return source.slice(firstBrace, i + 1);
      }
    }
  }

  throw new Error(`Could not find closing brace for ${constName}`);
}

function parseJsObjectLiteral(objectLiteral, nameForErrors) {
  try {
    return vm.runInNewContext(`(${objectLiteral})`, {}, { timeout: 5000 });
  } catch (err) {
    throw new Error(`Failed to parse ${nameForErrors}: ${err.message}`);
  }
}

function main() {
  const inputPath = process.argv[2];
  const outputPath = process.argv[3];

  if (!inputPath || !outputPath) {
    console.error(
      "Usage: node tools/convert-item-mapper.mjs <input itemMapper.ts> <output items.json>"
    );
    process.exit(1);
  }

  const source = fs.readFileSync(inputPath, "utf8");

  const pricesLiteral = extractObjectLiteral(source, "ITEM_PRICES");
  const descriptionsLiteral = extractObjectLiteral(source, "ITEM_DESCRIPTIONS");

  const prices = parseJsObjectLiteral(pricesLiteral, "ITEM_PRICES");
  const descriptions = parseJsObjectLiteral(descriptionsLiteral, "ITEM_DESCRIPTIONS");

  const output = {
    prices,
    descriptions
  };

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, JSON.stringify(output, null, 2), "utf8");

  console.log(`Wrote ${outputPath}`);
  console.log(`Price entries: ${Object.keys(prices).length}`);
  console.log(`Description entries: ${Object.keys(descriptions).length}`);
}

main();