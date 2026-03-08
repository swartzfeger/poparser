import fs from "fs";
import path from "path";
import vm from "vm";

function extractArrayLiteral(source, constName) {
  const marker = `const ${constName}`;
  const start = source.indexOf(marker);
  if (start === -1) {
    throw new Error(`Could not find ${constName}`);
  }

  const equalsIndex = source.indexOf("=", start);
  if (equalsIndex === -1) {
    throw new Error(`Could not find "=" for ${constName}`);
  }

  const firstBracket = source.indexOf("[", equalsIndex);
  if (firstBracket === -1) {
    throw new Error(`Could not find opening bracket for ${constName}`);
  }

  let depth = 0;
  let inString = false;
  let stringQuote = "";
  let escaped = false;

  for (let i = firstBracket; i < source.length; i++) {
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

    if (ch === "[") depth++;
    if (ch === "]") {
      depth--;
      if (depth === 0) {
        return source.slice(firstBracket, i + 1);
      }
    }
  }

  throw new Error(`Could not find closing bracket for ${constName}`);
}

function main() {
  const inputPath = process.argv[2];
  const outputPath = process.argv[3];

  if (!inputPath || !outputPath) {
    console.error("Usage: node tools/convert-customer-mapper.mjs <input customerMapper.ts> <output customers.json>");
    process.exit(1);
  }

  const source = fs.readFileSync(inputPath, "utf8");
  const arrayLiteral = extractArrayLiteral(source, "CUSTOMER_DATA");
  const customers = vm.runInNewContext(`(${arrayLiteral})`, {}, { timeout: 5000 });

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, JSON.stringify(customers, null, 2), "utf8");

  console.log(`Wrote ${outputPath}`);
  console.log(`Customer entries: ${customers.length}`);
}

main();