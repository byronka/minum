/**
 * Code for handling the harsh internet environment.
 * <p>
 *     In the modern internet/web, sites undergo constant abuse.  Scripts
 *     are run constantly by attackers to seek out security vulnerabilities.
 * </p>
 * <p>
 *     Many websites mitigate this by hiring services to protect themselves, placing
 *     themselves in a more protected position one step back from the full internet.
 * </p>
 * <p>
 *     This system was designed to be exposed out to the internet.  To avoid some
 *     of the most obvious attacks, the system looks for patterns indicating as
 *     much.  For example, there is no reason a user of the web application should
 *     need to access an endpoint called ".env", but many insecure sites will allow
 *     that file to be read, providing insight to attackers.  Thus, attackers will
 *     often request that file.  If we see that request, it is assumed we are
 *     getting a request from an attacker, and that ip address is put on a blacklist
 *     for a short time.
 * </p>
 */
package com.renomad.minum.security;