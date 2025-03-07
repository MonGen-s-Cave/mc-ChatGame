# FyreChatGame

## Miért?

- A mostani unalmas, és könnyű emellett fölösleges iterációkat végez el ami a processzorunknak nem jó.

## Milyen játékok vannak?

- Matek -> Random ELŐRE kiszámolt műveletek. Fontos, hogy előre ki legyen számolva, hogy ne terheljük a rendszert vele.
- Ki vagyok én? -> Ezek kérdések ahol meg van adva előre a válasz.
- Szókitaláló -> A betűk random össze lettek keverve a szóban
- Random karakterek -> Random generált karaktereket kell lemásolni
- Szóstop -> A szónak csak a kezdőbetűje van megadva és abból kell kitalálni a mobot!
- Kitöltő -> A szónak a hosszát elosztja 2-vel és annyi karaktert fog a szóban kicserélni _-re.
- Fordított -> A szó meg van fordítva.

## Miért jobb mint a ChatGames plugin?

### A kérdés jogos. Sorolom

- Középpontú abstract GameHandler osztály. Erre a struktúrára alapszik minden játék
- EnumBased kulcs kikérés
- Egyetlen Listener osztály (20 sor)
- EnumMap használata a rossz HashMap helyett ami nem thread-safe!
- A random generálás az a processzor száljain szétoszlatva megy így nem érzékelhető a folyamatokból semmi. Mintha előre be lenne táplálva a gépnek.
- Nincsenek folyamatos replacelések.
- Minden előre van kiszámolva és helyettesítve
- Modern Stream API használat! Ott van csak használva ahol indokolt így nem telitődik a memória.
- Modern Cache megoldás! Amint vége van egy játéknak amíg nem indul el egy új addig TELJESEN kiürít minden olyan memóriacímet ahol a plugin szerepel.
- Nincsen redundás kódrészlet.
- A játék így már nem olyan egyszerű mivel a helyes válaszokat nem írja ki a rendszer így mindig csak egy ember jegyezhet meg valamit, viszont a sebesség is számít ugye.

