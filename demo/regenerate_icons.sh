#!/bin/bash
set -e

ICON_SVG="app_icon.svg"

echo "Regenerating Android icons..."
rsvg-convert -w 48 -h 48 --keep-aspect-ratio -o androidApp/src/main/res/mipmap-mdpi/ic_launcher.png "$ICON_SVG"
rsvg-convert -w 72 -h 72 --keep-aspect-ratio -o androidApp/src/main/res/mipmap-hdpi/ic_launcher.png "$ICON_SVG"
rsvg-convert -w 96 -h 96 --keep-aspect-ratio -o androidApp/src/main/res/mipmap-xhdpi/ic_launcher.png "$ICON_SVG"
rsvg-convert -w 144 -h 144 --keep-aspect-ratio -o androidApp/src/main/res/mipmap-xxhdpi/ic_launcher.png "$ICON_SVG"
rsvg-convert -w 192 -h 192 --keep-aspect-ratio -o androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png "$ICON_SVG"

echo "Regenerating iOS icons..."
rsvg-convert -w 20 -h 20 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-20@1x.png "$ICON_SVG"
rsvg-convert -w 40 -h 40 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-20@2x.png "$ICON_SVG"
rsvg-convert -w 60 -h 60 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-20@3x.png "$ICON_SVG"
rsvg-convert -w 29 -h 29 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-29@1x.png "$ICON_SVG"
rsvg-convert -w 58 -h 58 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-29@2x.png "$ICON_SVG"
rsvg-convert -w 87 -h 87 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-29@3x.png "$ICON_SVG"
rsvg-convert -w 40 -h 40 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-40@1x.png "$ICON_SVG"
rsvg-convert -w 80 -h 80 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-40@2x.png "$ICON_SVG"
rsvg-convert -w 120 -h 120 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-40@3x.png "$ICON_SVG"
rsvg-convert -w 120 -h 120 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-60@2x.png "$ICON_SVG"
rsvg-convert -w 180 -h 180 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-60@3x.png "$ICON_SVG"
rsvg-convert -w 76 -h 76 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-76@1x.png "$ICON_SVG"
rsvg-convert -w 152 -h 152 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-76@2x.png "$ICON_SVG"
rsvg-convert -w 167 -h 167 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-83.5@2x.png "$ICON_SVG"
rsvg-convert -w 1024 -h 1024 --keep-aspect-ratio -o iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-1024.png "$ICON_SVG"

echo ""
echo "Icon regeneration complete!"
echo ""
echo "Verifying Android icons..."
for size in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  file androidApp/src/main/res/mipmap-$size/ic_launcher.png
done

echo ""
echo "Verifying iOS icons (sample)..."
file iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-20@1x.png
file iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-60@2x.png
file iosApp/StellarDemo/Assets.xcassets/AppIcon.appiconset/icon-1024.png
