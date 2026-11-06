// The app's keys for the services the screens ask: TMDB's, for the titles' original pictures (pictures.js). Empty
// here: the build puts the project's TMDB_KEY secret in (.github/workflows/build.yml), so the key isn't in the code
// anyone can read. Without one, a title's pictures are TMDB's only where one of its websites gives them.
'use strict';
TM.keys = { tmdb: '' };
