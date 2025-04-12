/**
 * This package contains classes for {@link com.renomad.minum.queue.ActionQueue}, which is
 * a background task processor.
 * <p>
 *     This enables programs to be run outside the normal request/response flow.  For example,
 *     <ul>
 *         <li>
 *             a computationally-heavy or long-running process run nightly on the data.
 *         </li>
 *         <li>
 *             An action by a user that could take a while to complete, such as compressing
 *             a large number of files.
 *         </li>
 *     </ul>
 * </p>
 * <p>
 *     A major difference between this and alternatives is its paradigm of lightness. Most
 *     background processors concern themselves with the potential risks - like if power goes out
 *     during a step, or if a remote endpoint fails, necessitating a retry.  If addressing risks
 *     such as those are prominent in your consideration, it may be worthwhile to use an
 *     alternative.
 * </p>
 * <p>
 *     But, if a prudent assessment is taken, in many cases the benefits of lightness and
 *     minimalism are sufficiently valuable to make the tradeoff worthwhile.  Minimalism
 *     makes the system harder against bugs of all types.  Everything has a tradeoff - using
 *     large complex systems is likelier to cause subtle bugs of all kinds - correctness,
 *     performance, security.
 * </p>
 */
package com.renomad.minum.queue;