## BookScraper

A simple webpage scraper implemented in Kotlin targeted towards a specific site for scraping webpage directory of the site
and storing it locally.

This project contains a config.properties file that allows you to configure parameters such as:

maxConcurrentJobs = max number of coroutines allowed to run in paralell.
maxDepth = maximum traversal depth of the recursive webscraper.
rootUrl = the targeted website.
destinationDir = where the output of the page is targeted.

The webscraper is recursive by nature with a maximum depth defined in the config file, 
to allow some sort of efficiency i have decided to implement this with kotlin coroutines for the depth traversal
of the scraper meaning that the main thread is not blocked by a single chain of links being traversed but it 
also allows for other links to be traversed and processed in paralell.

To ensure that we dont get into a situation where we could have transitive loops, i've implemented a simple hashmap
to log every page we have already processed. such that we simply ignore to process a page already traversed.
the write procedure to this hashmap is protected behind a sharedMutex lock to ensure that the coroutines have a syncronized
copy of the cache. Some third party libraries has been used to display progress, DOM manipulation and fetching of pages and media.

## How to build and run

Build:

./gradlew build

Run:

./gradlew run

Output: saved to the destinationDir defined in the config file.
