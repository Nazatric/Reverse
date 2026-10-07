/** The faint curved mesh behind the hub (the net fanning to the upper right in the original). */
export function Wireframe() {
  return (
    <svg className="wire" viewBox="0 0 800 800" preserveAspectRatio="none" aria-hidden="true" focusable="false">
      <path d="M160 620C242 516 335 390 415 316S618 214 764 208" />
      <path d="M165 632C272 536 362 426 447 339S643 230 774 230" />
      <path d="M162 605C259 501 366 400 484 315S668 242 779 256" />
      <path d="M162 581C275 483 395 381 522 312S688 260 779 279" />
      <path d="M163 558C287 462 418 368 555 311S712 278 779 304" />
      <path d="M170 538C306 441 442 365 585 322S729 299 777 332" />
      <path d="M180 519C321 429 470 366 612 341S743 323 769 358" />
      <path d="M198 500C350 417 496 375 637 361S752 354 761 386" />
      <path d="M221 483C373 413 524 390 661 386S755 386 750 417" />
      <path d="M246 470C408 414 559 409 685 415S754 419 737 448" />
      <path d="M272 460C438 420 592 433 705 443S746 453 720 480" />
      <path d="M295 452C469 432 617 456 718 474S736 487 707 509" />
      <path d="M318 446C498 446 643 484 729 506S725 522 690 541" />
      <path d="M341 440C525 464 664 513 735 539S708 558 673 573" />
      <path d="M182 617C362 561 552 480 759 214" />
      <path d="M207 630C394 570 584 482 763 234" />
      <path d="M239 637C429 583 610 495 768 260" />
      <path d="M275 639C464 598 637 514 768 291" />
      <path d="M315 638C497 611 654 534 765 324" />
      <path d="M357 634C529 624 669 560 756 360" />
      <path d="M401 627C558 636 681 591 745 399" />
      <path d="M442 617C587 646 691 622 730 442" />
      <ellipse cx="444" cy="441" rx="274" ry="158" transform="rotate(-19 444 441)" className="wire-ring" />
      <ellipse cx="438" cy="441" rx="312" ry="195" transform="rotate(-10 438 441)" className="wire-ring wire-ring--2" />
    </svg>
  );
}
