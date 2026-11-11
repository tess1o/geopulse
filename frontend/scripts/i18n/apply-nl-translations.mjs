#!/usr/bin/env node
/**
 * Pas resterende "same as English" vertalingen toe op alle nl-bestanden.
 * Alleen strings die echt vertaald moeten worden (niet productnamen, eenheden, URLs, technische termen).
 */
import { readFile, writeFile, readdir } from 'node:fs/promises'

const LOCALE_DIR = 'C:/Users/herbe/AppData/Local/Temp/opencode/geopulse-fork/frontend/src/locales/nl'

// Mapping: Engelse string -> Nederlandse vertaling
// Sleutels die al identiek zijn in NL (Status, Provider, Type, Start, Stop, etc.) worden weggelaten.
const TRANSLATIONS = {
    // missingActivity.js
    '"Transport"': '"Vervoer"',
    // profile/*.js
    "'{provider} icon'": "'{provider} pictogram'",
    "'Export'": "'Exporteren'",
    "'Import'": "'Importeren'",
    // timeline.js
    "'Map matching'": "'Kaartmatching'",
    "'Map matching details...'": "'Kaartmatching details...'",
    "'Data export is disabled in demo mode.'": "'Data-export is uitgeschakeld in demo-modus.'",
    "'Export is disabled in demo mode'": "'Export is uitgeschakeld in demo-modus'",
    // trips.js / data.js
    "'Transport'": "'Vervoer'",
    // sharing.js
    "'Auto: 5 sec'": "'Auto: 5 s'",
    "'Auto: 15 sec'": "'Auto: 15 s'",
    "'Auto: 30 sec'": "'Auto: 30 s'",
    // geofences.js
    "'{seconds} sec'": "'{seconds} s'",
    // adminSettings.js
    "'Release API URL'": "'Release API-URL'",
    "'Update Cache TTL'": "'Cache TTL bijwerken'",
    "'Update Connect Timeout'": "'Verbindingstimeout bijwerken'",
    "'Update Read Timeout'": "'Leestimeout bijwerken'",
    "'Water Connect Timeout'": "'Water verbindingstimeout'",
    "'Water Download Timeout'": "'Water downloadtimeout'",
    "'Water Stall Timeout'": "'Water stilstandtimeout'",
    "'Connect Timeout'": "'Verbindingstimeout'",
    "'Read Timeout'": "'Leestimeout'",
    // admin.js
    "'Client ID *'": "'Client ID *'",
    "'OAuth2 Client ID'": "'OAuth2 Client ID'",
    "'Client Secret {required}'": "'Client Secret {required}'",
    "'OAuth2 Client Secret'": "'OAuth2 Client Secret'",
    "'Discovery URL *'": "'Discovery URL *'",
    "'OIDC discovery endpoint URL'": "'OIDC discovery endpoint URL'",
    "'openid profile email'": "'openid profile email'",
    // tripDialogs.js al volledig vertaald via write
}

// Tellen hoeveel vervangingen per bestand
let totalReplacements = 0
const files = await readdir(LOCALE_DIR)

for (const file of files) {
    if (!file.endsWith('.js')) continue
    const path = `${LOCALE_DIR}/${file}`
    let content = await readFile(path, 'utf-8')
    let fileChanges = 0
    for (const [oldStr, newStr] of Object.entries(TRANSLATIONS)) {
        const count = (content.match(new RegExp(oldStr.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length
        if (count > 0) {
            content = content.replace(new RegExp(oldStr.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), newStr)
            fileChanges += count
        }
    }
    if (fileChanges > 0) {
        await writeFile(path, content, 'utf-8')
        console.log(`${file}: ${fileChanges} vervanging(en)`)
        totalReplacements += fileChanges
    }
}

console.log(`\nTotaal: ${totalReplacements} vervanging(en) in ${files.length} bestanden`)
