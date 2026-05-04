# One Enough — हिन्दी

## 1. परिचय

One Enough एक compatibility mod है, खासकर उन food और farming modpacks के लिए जो कई Farmer's Delight शैली के addons को साथ चलाते हैं। इसका लक्ष्य समान काम करने वाली सामग्री को एक जैसा व्यवहार देना है, भले ही उनके item ids अलग हों।

## 2. मुख्य क्षमताएँ

यह प्रोजेक्ट:

1. runtime पर item tags स्कैन करता है,
2. स्रोतों को conservative नियमों से classify करता है,
3. private और public unified tags प्रकाशित करता है,
4. कई source tags के सदस्यों को merge करता है,
5. simple hardcoded recipe ingredients को rewrite करता है,
6. classification results को cache करता है।

## 3. repository संरचना

- `common/`: shared logic, Mixins, resources
- `fabric/`: Fabric bootstrap
- `forge/`: Forge bootstrap
- `analysis/`: analysis scripts और outputs

## 4. runtime flow

मोड `OneEnoughMod.init()` से शुरू होता है और `TagGroupLoaderMixin` तथा `RecipeManagerMixin` के जरिए tags merge और recipes rewrite करता है।

## 5. automatic classification

classifier जानबूझकर conservative है: पहले helper tags हटाता है, फिर source strength जाँचता है, member names verify करता है और अलग acceptance thresholds लागू करता है।

## 6. configuration

`config/one-enough-mod.json` scan roots, allowlists, blocklists, aliases, item overrides, cache और recipe rewrite behavior को नियंत्रित करता है।

## 7. build

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

## 8. वर्तमान सीमाएँ

- configuration बदलने के बाद आम तौर पर restart चाहिए,
- केवल simple `{"item":"..."}` ingredient objects rewrite होते हैं,
- ambiguous items जानबूझकर skip किए जाते हैं,
- सिस्टम meaningful source tags पर निर्भर करता है।

## 9. संबंधित दस्तावेज़

- [classification-rules.md](../classification-rules.md)
- [review.md](../review.md)
- [analysis/github-delight-scan/README.md](../analysis/github-delight-scan/README.md)
