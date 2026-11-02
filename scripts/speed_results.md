# Speed results

I ran speed_test.py to try a few faster ways of encoding the bits

## Manchester (what we currently use)

| half | speed | result |
| --- | --- | --- |
| 0.1 s | 5 bits/s | works |
| 0.05 s | 10 bits/s | works |
| 0.04 s | 12.5 bits/s | fails |

## NRZI

| coding | piece | speed | runs | result |
| --- | --- | --- | --- | --- |
| raw NRZI | 50 ms | 20 bits/s | 1 | 10 symbol errors, 12/16 bytes |
| 4b5b + NRZI | 50 ms | 16 bits/s | 1 | no errors |
| 4b5b + NRZI | 40 ms | 20 bits/s | 2 | no errors both times |
| 4b5b + NRZI | 35 ms | 22.9 bits/s | 1 | no errors |
| 4b5b + NRZI | 30 ms | 26.7 bits/s | 2 | 82 symbol errors, then no sync |
| 4b5b + NRZI | 25 ms | 32 bits/s | 4 | no errors, then 44 errors, 49 errors, no sync |
| 4b5b + NRZI | 20 ms | 40 bits/s | 1 | 4 symbol errors, 14/16 bytes |
