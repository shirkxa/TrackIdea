#!/bin/sh
# Odznaka pokrycia testami dla README: czyta raport JaCoCo i zapisuje plik SVG.
#
#   ./mvnw verify && ./coverage-badge.sh
#
# Liczy pokrycie linii z target/site/jacoco/jacoco.csv (zaokrąglone w dół do pełnego procenta),
# zapisuje ../.github/badges/coverage.svg i wypisuje sam procent na standardowe wyjście.
set -eu

dir=$(cd "$(dirname "$0")" && pwd)
report=${1:-"$dir/target/site/jacoco/jacoco.csv"}
badge=${2:-"$dir/../.github/badges/coverage.svg"}

if [ ! -f "$report" ]; then
    echo "Brak raportu $report – najpierw uruchom ./mvnw verify" >&2
    exit 1
fi

# kolumny jacoco.csv: 8 = LINE_MISSED, 9 = LINE_COVERED
percent=$(awk -F, 'NR > 1 { missed += $8; covered += $9 }
    END { total = missed + covered; print total ? int(covered * 100 / total) : 0 }' "$report")

if [ "$percent" -ge 90 ]; then color="#4c1"
elif [ "$percent" -ge 80 ]; then color="#97ca00"
elif [ "$percent" -ge 70 ]; then color="#a4a61d"
elif [ "$percent" -ge 60 ]; then color="#dfb317"
elif [ "$percent" -ge 50 ]; then color="#fe7d37"
else color="#e05d44"
fi

label="coverage"
value="$percent%"
label_width=61
value_width=$((11 + 8 * ${#value}))
width=$((label_width + value_width))
label_x=$((label_width * 5 + 10))
value_x=$((label_width * 10 + value_width * 5 - 10))
label_length=$(((label_width - 10) * 10))
value_length=$(((value_width - 10) * 10))

mkdir -p "$(dirname "$badge")"
cat > "$badge" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="20" role="img" aria-label="$label: $value">
  <title>$label: $value</title>
  <linearGradient id="s" x2="0" y2="100%">
    <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
    <stop offset="1" stop-opacity=".1"/>
  </linearGradient>
  <clipPath id="r">
    <rect width="$width" height="20" rx="3" fill="#fff"/>
  </clipPath>
  <g clip-path="url(#r)">
    <rect width="$label_width" height="20" fill="#555"/>
    <rect x="$label_width" width="$value_width" height="20" fill="$color"/>
    <rect width="$width" height="20" fill="url(#s)"/>
  </g>
  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="110">
    <text aria-hidden="true" x="$label_x" y="150" fill="#010101" fill-opacity=".3" transform="scale(.1)" textLength="$label_length">$label</text>
    <text x="$label_x" y="140" transform="scale(.1)" textLength="$label_length">$label</text>
    <text aria-hidden="true" x="$value_x" y="150" fill="#010101" fill-opacity=".3" transform="scale(.1)" textLength="$value_length">$value</text>
    <text x="$value_x" y="140" transform="scale(.1)" textLength="$value_length">$value</text>
  </g>
</svg>
EOF

echo "$percent"
