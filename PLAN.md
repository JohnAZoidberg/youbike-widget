I would like to create an android widget that shows me the status of a few youbike stations.
Youbike is the rental bike system in Taipei where I live. Each station can have empty and occupied bike racks.
So each station could either be empty, half occupied or fully occupied, which means either I cannot get a bike or i can return/rent or I can only rent but not return.

I would like to have a widget that shows me the 2 nearest stations with their distance in meters and also up to 5 of my favorite stations (If we can avoid an app we can configure this when building the app, maybe later we can build an app to configure this).
The widget is a table that looks like this:

| Name | Distance | Spots | Bikes | E-Bikes |
| Foo  |     50 m |     0 |    10 |       5 |
| Baz  |    120 m |    21 |     4 |       2 |
| Bar  |    500 m |    12 |     0 |       0 |

And I don't need to update this super frequently, maybe every 10 minutes in the background.
If we can we should add a small timestamp at the bottom to let me know how fresh the data is.

The widget needs to run on Android 15 and up.

We can get the data here: https://tcgbusfs.blob.core.windows.net/dotapp/youbike/v2/youbike_immediate.json

We're on NixOS, so if we can use nix flakes to get all dependencies that would be great.
Make sure it can also be built on github actions and deliver us an APK.
