- [x] In search results when the result is a video prevent it from autoplaying; a user has to "hover" on it to play. When it's done playing, if the next result is also a video then autoplay it.
- [ ] Clicking on partner name in chat should send me to their profile
- [ ] Add dates in the format of --------------- 2 days ago -------------- etc in chat to group the chats by date within the UI


1. Authentication and Token Management:


   * Concern: Unencrypted `SharedPreferences` for Tokens
       * Mitigation: Implement EncryptedSharedPreferences from AndroidX Security library. This encrypts keys and values automatically.
       * Action:
           1. Add implementation "androidx.security:security-crypto:1.1.0-alpha06" to app/build.gradle.kts.
           2. Modify AuthActivity.kt and AuthUtils.kt to use EncryptedSharedPreferences instead of plain SharedPreferences.


   * Concern: Hardcoded API Keys (`SUPABASE_ANON_KEY`, `FLUTTERWAVE_PUBLIC_KEY`)
       * Mitigation: Store sensitive keys outside of version control and inject them at build time.
       * Action:
           1. Define these keys in local.properties (which is .gitignored) or as environment variables.
           2. In app/build.gradle.kts, read these properties and expose them as BuildConfig fields.
           3. Update SupabaseConfig.kt to retrieve these values from BuildConfig.


   * Concern: Client-side JWT Decoding for `user_id` without Backend Re-verification
       * Mitigation: Ensure the backend always re-verifies the JWT and performs authorization checks based on the server-side validated user ID.
       * Action: (This is a backend-side fix, but crucial for overall security)
           1. Backend Development: Confirm that all API endpoints requiring authentication validate the JWT's signature and expiration, and extract the
              user ID from the validated token for authorization decisions. Do not trust the user_id sent directly from the client in request bodies for
              sensitive operations.


2. Deep Link Handling:


   * Concern: Vulnerable to Deep Link Hijacking
       * Mitigation: Implement strict validation of incoming deep link URIs and consider using Android App Links for verified ownership.
       * Action:
           1. In `AuthActivity.kt` and `PostsActivity.kt`: Before processing any data from intent.data, add checks to verify the uri.host and uri.scheme
              against expected values.
           2. Consider Android App Links: For production, implement Android App Links (Digital Asset Links) to verify ownership of gifters.club domain,
              preventing other apps from intercepting your web links. This involves hosting a assetlinks.json file on your domain.


3. Input Validation and Sanitization:


   * Concern: No Client-side Validation/Sanitization for User-Generated Content
       * Mitigation: Add client-side validation to provide immediate feedback to users and reduce invalid requests to the backend.
       * Action:
           1. In `CreatePostFragment.kt` (for `etContent`): Add length constraints and potentially basic regex patterns if specific content formats are
              expected.
           2. In `CommentsBottomSheetFragment.kt` (for `etComment`): Similar to posts, add length checks and basic sanitization if needed.


   * Concern: Mass Assignment Vulnerability with `Map<String, Any>`
       * Mitigation: Use specific data classes for API request bodies to enforce expected fields.
       * Action:
           1. In `CommentApiHolder.kt`: Instead of Map<String, @JvmSuppressWildcards Any>, define a data class CommentReactionRequest(val comment_id:
              String, val user_id: String, val type: String) and use that for reactToComment. Similarly for createComment.
           2. Refactor: Review all API calls that use Map<String, Any> for request bodies and replace them with strongly typed data classes.

5. Access Control and Authorization:


   * Concern: Client-side Enforcement Not Clear; Backend Must Be Robust
       * Mitigation: This is primarily a backend responsibility, but the client should never assume access.
       * Action: (Reinforce existing good practices and ensure backend alignment)
           1. Backend Development: Ensure that the Supabase Row Level Security (RLS) policies are correctly configured and strictly enforced for all tables
              (e.g., posts, post_media, comments, subscriptions, post_access).
           2. Client-side: Continue to use the onLocked callback and UI elements to guide users, but understand that the ultimate access decision rests with
              the backend.

6. Third-Party Libraries:


   * Concern: Outdated Libraries
       * Mitigation: Regularly update dependencies and use dependency scanning tools.
       * Action:
           1. Regular Updates: Periodically check for newer versions of all libraries in app/build.gradle.kts and build.gradle.kts and update them.
           2. Dependency Scanning: Integrate a dependency vulnerability scanner (e.g., OWASP Dependency-Check, Snyk) into your CI/CD pipeline to
              automatically detect known vulnerabilities in your dependencies.

7. Android Settings Screen Enhancements:

   • Scaffold the SettingsFragment with tabs for Profile, Security, Moderation, Interaction.
   • Implement ProfileSettingsFragment to edit username, display name, bio, and upload + crop a square avatar.
   • Implement SecuritySettingsFragment to block/unblock users, report users, and show user’s reports.
   • Implement ModerationSettingsFragment to add/remove filtered words with spinners and toasts.
   • Implement InteractionSettingsFragment to select who_can_interact (anyone, followers, friends) with persistence.



