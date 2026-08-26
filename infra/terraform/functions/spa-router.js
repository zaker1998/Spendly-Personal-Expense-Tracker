// Maps Angular's client-side routes onto the single index.html object.
//
// The usual recipe for this is a CloudFront custom_error_response turning 403
// and 404 into 200 /index.html. That cannot be used here: custom error
// responses apply to the whole distribution, and this one also fronts the API --
// a 404 for a missing expense or a 403 from an admin endpoint would come back as
// 200 with an HTML body, which would quietly destroy the API's error contract.
//
// A viewer-request function is attached to the SPA behaviour only, so the API
// path is untouched.
//
// Written for the ES5.1 runtime, so no template literals, includes() or arrows.
function handler(event) {
    var request = event.request;
    var uri = request.uri;

    if (uri.charAt(uri.length - 1) === '/') {
        request.uri = uri + 'index.html';
        return request;
    }

    // A dot in the last path segment means a real file (main-ABC123.js,
    // favicon.ico). Anything else is a route that only exists in the browser.
    if (uri.indexOf('.', uri.lastIndexOf('/')) === -1) {
        request.uri = '/index.html';
    }

    return request;
}
